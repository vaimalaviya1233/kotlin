/*
* Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
* that can be found in the LICENSE file.
*/

#pragma once

#include <condition_variable>
#include <cstdint>
#include <functional>
#include <mutex>

#include "KAssert.h"
#include "Memory.h"
#include "Runtime.h"
#include "ScopedThread.hpp"

namespace kotlin::gc {

template <typename Queue>
class FinalizerProcessor : private Pinned {
public:
    explicit FinalizerProcessor(std::function<void(int64_t)> epochDoneCallback) noexcept : epochDoneCallback_(std::move(epochDoneCallback)) {}

    ~FinalizerProcessor() {
        StopFinalizerThread();
    }

    void ScheduleTasks(Queue tasks, int64_t epoch) noexcept {
        std::unique_lock guard(finalizerQueueMutex_);
        if (tasks.size() == 0 && !IsRunning()) {
            epochDoneCallback_(epoch);
            return;
        }
        finalizerQueueCondVar_.wait(guard, [this] { return newTasksAllowed_; });
        StartFinalizerThreadIfNone();
        finalizerQueue_.MergeWith(std::move(tasks));
        finalizerQueueEpoch_ = epoch;
        finalizerQueueCondVar_.notify_all();
    }

    void StopFinalizerThread() noexcept {
        {
            std::unique_lock guard(finalizerQueueMutex_);
            if (!finalizerThread_.joinable()) return;
            shutdownFlag_ = true;
            finalizerQueueCondVar_.notify_all();
        }
        finalizerThread_.join();
        shutdownFlag_ = false;
        RuntimeAssert(finalizerQueue_.size() == 0, "Finalizer queue should be empty when killing finalizer thread");
        std::unique_lock guard(finalizerQueueMutex_);
        newTasksAllowed_ = true;
        finalizerQueueCondVar_.notify_all();
    }

    bool IsRunning() noexcept {
        return finalizerThread_.joinable();
    }

    void StartFinalizerThreadIfNone() noexcept {
        std::unique_lock guard(threadCreatingMutex_);
        if (finalizerThread_.joinable()) return;

        finalizerThread_ = ScopedThread(ScopedThread::attributes().name("GC finalizer processor"), &FinalizerProcessor::FinalizerRoutine, this);
    }

    void WaitFinalizerThreadInitialized() noexcept {
        std::unique_lock guard(initializedMutex_);
        initializedCondVar_.wait(guard, [this] { return initialized_; });
    }


private:
    void FinalizerRoutine() noexcept {
        Kotlin_initRuntimeIfNeeded();
        {
            std::unique_lock guard(initializedMutex_);
            initialized_ = true;
        }
        initializedCondVar_.notify_all();
        int64_t finalizersEpoch = 0;
        while (true) {
            std::unique_lock lock(finalizerQueueMutex_);
            finalizerQueueCondVar_.wait(lock, [this, &finalizersEpoch] {
                return finalizerQueue_.size() > 0 || finalizerQueueEpoch_ != finalizersEpoch || shutdownFlag_;
            });
            if (finalizerQueue_.size() == 0 && finalizerQueueEpoch_ == finalizersEpoch) {
                newTasksAllowed_ = false;
                RuntimeAssert(shutdownFlag_, "Nothing to do, but no shutdownFlag_ is set on wakeup");
                break;
            }
            auto queue = std::move(finalizerQueue_);
            finalizersEpoch = finalizerQueueEpoch_;
            lock.unlock();
            if (queue.size() > 0) {
                ThreadStateGuard guard(ThreadState::kRunnable);
                queue.Finalize();
            }
            epochDoneCallback_(finalizersEpoch);
        }
        {
            std::unique_lock guard(initializedMutex_);
            initialized_ = false;
        }
        initializedCondVar_.notify_all();
    }

    ScopedThread finalizerThread_;
    Queue finalizerQueue_;
    std::condition_variable finalizerQueueCondVar_;
    std::mutex finalizerQueueMutex_;
    std::function<void(int64_t)> epochDoneCallback_;
    int64_t finalizerQueueEpoch_ = 0;
    bool shutdownFlag_ = false;
    bool newTasksAllowed_ = true;

    std::mutex initializedMutex_;
    std::condition_variable initializedCondVar_;
    bool initialized_ = false;

    std::mutex threadCreatingMutex_;
};

} // namespace kotlin::gc
