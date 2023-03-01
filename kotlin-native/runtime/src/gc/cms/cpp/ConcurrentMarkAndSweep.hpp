/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <atomic>
#include <cstddef>

#include "Allocator.hpp"
#include "GCScheduler.hpp"
#include "GCState.hpp"
#include "GCStatistics.hpp"
#include "IntrusiveList.hpp"
#include "MarkAndSweepUtils.hpp"
#include "ScopedThread.hpp"
#include "ThreadData.hpp"
#include "Types.h"
#include "Utils.hpp"
#include "std_support/Memory.hpp"

namespace kotlin {
namespace gc {

// Stop-the-world parallel mark + concurrent sweep. The GC runs in a separate thread, finalizers run in another thread of their own.
// TODO: Also make marking run concurrently with Kotlin threads.
class ConcurrentMarkAndSweep : private Pinned {
public:
    class ObjectData {
    public:
        bool tryMark() noexcept {
            return trySetNext(reinterpret_cast<ObjectData*>(1));
        }

        bool marked() const noexcept { return next() != nullptr; }

        bool tryResetMark() noexcept {
            if (next() == nullptr) return false;
            next_.store(nullptr, std::memory_order_relaxed);
            return true;
        }

    private:
        friend struct DefaultIntrusiveForwardListTraits<ObjectData>;

        ObjectData* next() const noexcept { return next_.load(std::memory_order_relaxed); }
        void setNext(ObjectData* next) noexcept {
            RuntimeAssert(next, "next cannot be nullptr");
            next_.store(next, std::memory_order_relaxed);
        }
        bool trySetNext(ObjectData* next) noexcept {
            RuntimeAssert(next, "next cannot be nullptr");
            ObjectData* expected = nullptr;
            return next_.compare_exchange_strong(expected, next, std::memory_order_relaxed);
        }

        std::atomic<ObjectData*> next_ = nullptr;
    };

    static inline constexpr size_t ObjectDataAlignment = alignof(ObjectData);
    static inline constexpr size_t ObjectDataSize = sizeof(ObjectData);

    enum MarkingBehavior { kMarkOwnStack, kDoNotMark };

    using MarkQueue = intrusive_forward_list<ObjectData>;

    class ThreadData : private Pinned {
    public:
        using ObjectData = ConcurrentMarkAndSweep::ObjectData;

        explicit ThreadData(
                ConcurrentMarkAndSweep& gc, mm::ThreadData& threadData, gcScheduler::GCSchedulerThreadData& gcScheduler) noexcept :
            gc_(gc), threadData_(threadData) {}
        ~ThreadData() = default;

        void Schedule() noexcept;
        void ScheduleAndWaitFullGC() noexcept;
        void ScheduleAndWaitFullGCWithFinalizers() noexcept;

        void OnSuspendForGC() noexcept;

    private:
        friend ConcurrentMarkAndSweep;
        ConcurrentMarkAndSweep& gc_;
        mm::ThreadData& threadData_;
        std::atomic<bool> marking_;
    };

    ConcurrentMarkAndSweep(gcScheduler::GCScheduler& scheduler, alloc::Allocator& allocator) noexcept;
    ~ConcurrentMarkAndSweep();

    void SetMarkingBehaviorForTests(MarkingBehavior markingBehavior) noexcept;
    void SetMarkingRequested(uint64_t epoch) noexcept;
    void WaitForThreadsReadyToMark() noexcept;
    void CollectRootSetAndStartMarking(GCHandle gcHandle) noexcept;

    void Schedule() noexcept { state_.schedule(); }

private:
    void PerformFullGC(int64_t epoch) noexcept;

    gcScheduler::GCScheduler& gcScheduler_;
    alloc::Allocator& allocator_;

    GCStateHolder state_;
    ScopedThread gcThread_;

    MarkQueue markQueue_;
    MarkingBehavior markingBehavior_;
};

namespace internal {
struct MarkTraits {
    using MarkQueue = gc::ConcurrentMarkAndSweep::MarkQueue;

    static void clear(MarkQueue& queue) noexcept { queue.clear(); }

    static ObjHeader* tryDequeue(MarkQueue& queue) noexcept {
        if (auto* top = queue.try_pop_front()) {
            return alloc::Allocator::objectForData(top);
        }
        return nullptr;
    }

    static bool tryEnqueue(MarkQueue& queue, ObjHeader* object) noexcept {
        auto& objectData = *static_cast<ConcurrentMarkAndSweep::ObjectData*>(alloc::Allocator::dataForObject(object));
        return queue.try_push_front(objectData);
    }

    static bool tryMark(ObjHeader* object) noexcept {
        auto& objectData = *static_cast<ConcurrentMarkAndSweep::ObjectData*>(alloc::Allocator::dataForObject(object));
        return objectData.tryMark();
    }

    static void processInMark(MarkQueue& markQueue, ObjHeader* object) noexcept {
        auto process = object->type_info()->processObjectInMark;
        RuntimeAssert(process != nullptr, "Got null processObjectInMark for object %p", object);
        process(static_cast<void*>(&markQueue), object);
    }
};
} // namespace internal

} // namespace gc
} // namespace kotlin
