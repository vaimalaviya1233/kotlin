/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "../CompilerConstants.hpp"
#include "../KAssert.h"
#include "../Logging.hpp"
#include "../Utils.hpp"
#include "Porting.h"
#include "PushOnlyAtomicArray.hpp"
#include "SplitSharedList.hpp"

namespace kotlin {

namespace internal {

enum class ShareOn { kPush, kPop };

} // namespace internal

/**
 * Coordinates a group of workers working in parallel on a large amounts of identical tasks.
 * The dispatcher will try to balance the work among the workers.
 *
 * In order for the work to be completed:
 * 1.  There must be exactly `expectedWorkers()` number of workers instantiated;
 * 2.  Every worker must execute `performWork` sooner or later;
 * 3.  No work must be pushed into a worker's work list from outside (by any means other than `serialWorkProcessor`)
 *     after the start of `performWork` execution.
 */
template <std::size_t kMaxWorkers, typename ListImpl, std::size_t kMinSizeToShare, std::size_t kMaxSizeToSteal = kMinSizeToShare / 2, internal::ShareOn kShareOn = internal::ShareOn::kPush>
class ParallelProcessor : private Pinned {
public:
    static const std::size_t kStealingAttemptCyclesBeforeWait = 4;

    class Worker : private Pinned {
        friend ParallelProcessor;
    public:
        explicit Worker(ParallelProcessor& dispatcher) : dispatcher_(dispatcher) {
            dispatcher_.registerWorker(*this);
        }

        ~Worker() {
            RuntimeAssert(empty(), "There should be no local tasks left");
            RuntimeAssert(dispatcher_.allDone_.load(std::memory_order_relaxed), "Work must be done");

            // TODO: I don't understand the need for below

            std::size_t expected = 0;
            dispatcher_.workersWaitingForTermination_.compare_exchange_strong(expected, dispatcher_.registeredWorkers_.size());

            RuntimeLogDebug({ "balancing" }, "Worker waits for others to terminate");
            while (dispatcher_.waitingWorkers_.load(std::memory_order_relaxed) > 0) {
                std::this_thread::yield();
            }

            --dispatcher_.workersWaitingForTermination_;
        }

        bool empty() const noexcept {
            return list_.localEmpty() && list_.sharedEmpty();
        }

        bool tryPush(typename ListImpl::reference value) noexcept {
            bool pushed = list_.tryPushLocal(value);
            if (pushed && kShareOn == internal::ShareOn::kPush) {
                shareAll();
            }
            return pushed;
        }

        typename ListImpl::pointer tryPop() noexcept {
            while (true) {
                if (auto popped = list_.tryPopLocal()) {
                    if (kShareOn == internal::ShareOn::kPop) {
                        shareAll();
                    }
                    return popped;
                }
                if (tryAcquireWork()) {
                    continue;
                }
                break;
            }
            return nullptr;
        }

    private:
        bool tryTransferFromLocal() noexcept {
            // check own shared queue first
            auto selfStolen = list_.tryTransferFrom(list_, kMaxSizeToSteal);
            if (selfStolen > 0) {
                RuntimeLogDebug({"balancing"}, "Worker has acquired %zu tasks from itself", selfStolen);
                return true;
            }
            return false;
        }

        bool tryTransferFromCooperating() {
            for (size_t i = 0; i < kStealingAttemptCyclesBeforeWait; ++i) {
                for (auto& fromAtomic : dispatcher_.registeredWorkers_) {
                    auto& from = *fromAtomic.load(std::memory_order_relaxed);
                    auto stolen = list_.tryTransferFrom(from.list_, kMaxSizeToSteal);
                    if (stolen > 0) {
                        RuntimeLogDebug({"balancing"}, "Worker has acquired %zu tasks from %d", stolen, from.carrierThreadId_);
                        return true;
                    }
                }
                std::this_thread::yield();
            }
            return false;
        }

        bool tryAcquireWork() noexcept {
            if (tryTransferFromLocal())
                return true;
            if (tryTransferFromCooperating())
                return true;

            RuntimeLogDebug({"balancing"}, "Worker has not found a victim to steal from :(");

            return waitForMoreWork();
        }

        bool waitForMoreWork() noexcept {
            std::unique_lock lock(dispatcher_.waitMutex_);

            auto nowWaiting = dispatcher_.waitingWorkers_.fetch_add(1, std::memory_order_relaxed) + 1;
            RuntimeLogDebug({ "balancing" },
                            "Worker goes to sleep (now sleeping %zu registered %zu expected %zu)",
                            nowWaiting,
                            dispatcher_.registeredWorkers_.size(),
                            dispatcher_.expectedWorkers_.load());

            if (dispatcher_.allDone_) {
                dispatcher_.waitingWorkers_.fetch_sub(1, std::memory_order_relaxed);
                return false;
            }

            auto registeredWorkers = dispatcher_.registeredWorkers_.size();
            if (nowWaiting == registeredWorkers
                    && registeredWorkers == dispatcher_.expectedWorkers_.load(std::memory_order_relaxed)) {
                // we are the last ones awake
                RuntimeLogDebug({ "balancing" }, "Worker has detected termination");
                dispatcher_.allDone_ = true;
                lock.unlock();
                dispatcher_.waitCV_.notify_all();
                dispatcher_.waitingWorkers_.fetch_sub(1, std::memory_order_relaxed);
                return false;
            }

            dispatcher_.waitCV_.wait(lock);
            dispatcher_.waitingWorkers_.fetch_sub(1, std::memory_order_relaxed);
            if (dispatcher_.allDone_) {
                return false;
            }
            RuntimeLogDebug({ "balancing" }, "Worker woke up");

            return true;
        }

        void shareAll() noexcept {
            if (list_.localSize() > kMinSizeToShare) {
                auto shared = list_.shareAllWith(list_);
                if (shared > 0) {
                    dispatcher_.onShare(shared);
                }
            }
        }

        const int carrierThreadId_ = konan::currentThreadId();
        ParallelProcessor& dispatcher_;
        SplitSharedList<ListImpl> list_;
    };

    explicit ParallelProcessor(size_t expectedWorkers) : expectedWorkers_(expectedWorkers) {}

    ~ParallelProcessor() {
        RuntimeAssert(waitingWorkers_.load() == 0, "All the workers must terminate before dispatcher destruction");
        while (workersWaitingForTermination_ > 0) {
            std::this_thread::yield();
        }
    }

    void lowerExpectations(size_t nowExpectedWorkers) {
        RuntimeAssert(nowExpectedWorkers <= kMaxWorkers,
                      "WorkBalancingDispatcher supports max %zu workers, but %zu requested",
                      kMaxWorkers,
                      nowExpectedWorkers);
        RuntimeAssert(nowExpectedWorkers <= expectedWorkers_, "Previous expectation must have been not less");
        RuntimeAssert(nowExpectedWorkers >= registeredWorkers_.size(), "Can't set expectations lower than the number of already registered workers");
        expectedWorkers_ = nowExpectedWorkers;
        RuntimeAssert(registeredWorkers_.size() <= expectedWorkers_, "Must not have registered more jobs than expected");
    }

    size_t expectedWorkers() {
        return expectedWorkers_.load(std::memory_order_relaxed);
    }

    size_t registeredWorkers() {
        return registeredWorkers_.size(std::memory_order_relaxed);
    }

private:
    void registerWorker(Worker& worker) {
        RuntimeAssert(worker.empty(), "Work list of an unregistered worker must be empty (e.g. fully depleted earlier)");
        RuntimeAssert(!allDone_, "Dispatcher must wait for every possible worker to register before finishing the work");
        RuntimeAssert(!isRegistered(worker), "Task registration is not idempotent");

        RuntimeAssert(registeredWorkers_.size() + 1 <= expectedWorkers_, "Impossible to register more tasks than expected");
        registeredWorkers_.push(&worker);
        RuntimeLogDebug({ "balancing" }, "Worker registered");

        if (registeredWorkers_.size() == expectedWorkers_) {
            RuntimeLogDebug({ "balancing" }, "All the expected workers registered");
        }
    }

    // Primarily to be used in assertions
    bool isRegistered(const Worker& worker) const {
        for (size_t i = 0; i < registeredWorkers_.size(std::memory_order_acquire); ++i) {
            if (registeredWorkers_[i] == &worker) return true;
        }
        return false;
    }

    void onShare(std::size_t sharedAmount) {
        RuntimeAssert(sharedAmount > 0, "Must have shared something");
        RuntimeLogDebug({ "balancing" }, "Worker has shared %zu tasks", sharedAmount);
        if (waitingWorkers_.load(std::memory_order_relaxed) > 0) {
            waitCV_.notify_all();
        }
    }

    PushOnlyAtomicArray<Worker*, kMaxWorkers, nullptr> registeredWorkers_;
    std::atomic<size_t> expectedWorkers_ = 0;
    std::atomic<size_t> waitingWorkers_ = 0;

    // special counter indicating the number of workers that can still spin in some loop and read one of the processor's fields
    std::atomic<size_t> workersWaitingForTermination_ = 0;

    std::atomic<bool> allDone_ = false;
    mutable std::mutex waitMutex_;
    mutable std::condition_variable waitCV_;
};

}
