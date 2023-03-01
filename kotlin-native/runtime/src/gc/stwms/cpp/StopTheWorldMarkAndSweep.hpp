/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <cstddef>

#include "Allocator.hpp"
#include "GCScheduler.hpp"
#include "GCState.hpp"
#include "IntrusiveList.hpp"
#include "ScopedThread.hpp"
#include "Types.h"
#include "Utils.hpp"

namespace kotlin {

namespace mm {
class ThreadData;
}

namespace gc {

// Stop-the-world mark&sweep. The GC runs in a separate thread, finalizers run in another thread of their own.
class StopTheWorldMarkAndSweep : private Pinned {
public:
    class ObjectData {
    public:
        bool tryMark() noexcept { return trySetNext(reinterpret_cast<ObjectData*>(1)); }

        bool marked() const noexcept { return next_ != nullptr; }

        bool tryResetMark() noexcept {
            if (next_ == nullptr) return false;
            next_ = nullptr;
            return true;
        }

    private:
        friend struct DefaultIntrusiveForwardListTraits<ObjectData>;

        ObjectData* next() const noexcept { return next_; }
        void setNext(ObjectData* next) noexcept {
            RuntimeAssert(next, "next cannot be nullptr");
            next_ = next;
        }
        bool trySetNext(ObjectData* next) noexcept {
            RuntimeAssert(next, "next cannot be nullptr");
            if (next_ != nullptr) {
                return false;
            }
            next_ = next;
            return true;
        }

        ObjectData* next_ = nullptr;
    };

    using MarkQueue = intrusive_forward_list<ObjectData>;

    class ThreadData : private Pinned {
    public:
        using ObjectData = StopTheWorldMarkAndSweep::ObjectData;

        ThreadData(StopTheWorldMarkAndSweep& gc, mm::ThreadData& threadData, gcScheduler::GCSchedulerThreadData& gcScheduler) noexcept :
            gc_(gc) {}
        ~ThreadData() = default;

        void Schedule() noexcept;
        void ScheduleAndWaitFullGC() noexcept;
        void ScheduleAndWaitFullGCWithFinalizers() noexcept;

    private:
        StopTheWorldMarkAndSweep& gc_;
    };

    StopTheWorldMarkAndSweep(gcScheduler::GCScheduler& gcScheduler, alloc::Allocator& allocator) noexcept;
    ~StopTheWorldMarkAndSweep();

    void StartFinalizerThreadIfNeeded() noexcept;
    void StopFinalizerThreadIfRunning() noexcept;
    bool FinalizersThreadIsRunning() noexcept;

    void Schedule() noexcept { state_.schedule(); }

private:
    void PerformFullGC(int64_t epoch) noexcept;

    gcScheduler::GCScheduler& gcScheduler_;
    alloc::Allocator& allocator_;

    GCStateHolder state_;
    ScopedThread gcThread_;

    MarkQueue markQueue_;
};

namespace internal {

struct MarkTraits {
    using MarkQueue = gc::StopTheWorldMarkAndSweep::MarkQueue;

    static void clear(MarkQueue& queue) noexcept { queue.clear(); }

    static ObjHeader* tryDequeue(MarkQueue& queue) noexcept {
        if (auto* top = queue.try_pop_front()) {
            return alloc::Allocator::objectForData(top);
        }
        return nullptr;
    }

    static bool tryEnqueue(MarkQueue& queue, ObjHeader* object) noexcept {
        auto& objectData = *static_cast<StopTheWorldMarkAndSweep::ObjectData*>(alloc::Allocator::dataForObject(object));
        return queue.try_push_front(objectData);
    }

    static bool tryMark(ObjHeader* object) noexcept {
        auto& objectData = *static_cast<StopTheWorldMarkAndSweep::ObjectData*>(alloc::Allocator::dataForObject(object));
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
