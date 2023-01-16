/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <cstddef>

#include "Allocator.hpp"
#include "FinalizerProcessor.hpp"
#include "GCStatistics.hpp"
#include "IntrusiveList.hpp"
#include "ObjectFactory.hpp"
#include "Types.h"
#include "Utils.hpp"

namespace kotlin {

namespace mm {
class ThreadData;
}

namespace gc {

// Stop-the-world Mark-and-Sweep.
class StopTheWorldMarkAndSweep : private Pinned {
public:
    class ObjectData {
    public:
        bool tryMark() noexcept {
            return trySetNext(reinterpret_cast<ObjectData*>(1));
        }

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
        using Allocator = AllocatorWithGC<Allocator>;

        ThreadData(StopTheWorldMarkAndSweep& gc, mm::ThreadData& threadData) noexcept {}
        ~ThreadData() = default;
    };

    using Allocator = ThreadData::Allocator;

    explicit StopTheWorldMarkAndSweep(mm::ObjectFactory<StopTheWorldMarkAndSweep>& objectFactory) noexcept;
    ~StopTheWorldMarkAndSweep() = default;

    void StartFinalizerThreadIfNeeded() noexcept;
    void StopFinalizerThreadIfRunning() noexcept;
    bool FinalizersThreadIsRunning() noexcept;

    void RunGC(GCHandle& handle) noexcept;

private:
    mm::ObjectFactory<StopTheWorldMarkAndSweep>& objectFactory_;

    FinalizerProcessor<mm::ObjectFactory<StopTheWorldMarkAndSweep>::FinalizerQueue> finalizerProcessor_;

    MarkQueue markQueue_;
};

namespace internal {

struct MarkTraits {
    using MarkQueue = gc::StopTheWorldMarkAndSweep::MarkQueue;

    static void clear(MarkQueue& queue) noexcept { queue.clear(); }

    static ObjHeader* tryDequeue(MarkQueue& queue) noexcept {
        if (auto* top = queue.try_pop_front()) {
            auto node = mm::ObjectFactory<gc::StopTheWorldMarkAndSweep>::NodeRef::From(*top);
            return node->GetObjHeader();
        }
        return nullptr;
    }

    static bool tryEnqueue(MarkQueue& queue, ObjHeader* object) noexcept {
        auto& objectData = mm::ObjectFactory<gc::StopTheWorldMarkAndSweep>::NodeRef::From(object).ObjectData();
        return queue.try_push_front(objectData);
    }

    static bool tryMark(ObjHeader* object) noexcept {
        auto& objectData = mm::ObjectFactory<gc::StopTheWorldMarkAndSweep>::NodeRef::From(object).ObjectData();
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
