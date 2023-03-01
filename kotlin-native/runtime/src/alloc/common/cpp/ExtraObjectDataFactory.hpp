/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "ExtraObjectData.hpp"
#include "GC.hpp"
#include "GCStatistics.hpp"
#include "Memory.h"
#include "MultiSourceQueue.hpp"
#include "ObjectAlloc.hpp"
#include "ThreadRegistry.hpp"

namespace kotlin::alloc {

// Registry for extra data, attached to some kotlin objects: weak refs, associated objects, ...
class ExtraObjectDataFactory : Pinned {
    using Mutex = SpinLock<MutexThreadStateHandling::kIgnore>;
    using Queue = MultiSourceQueue<mm::ExtraObjectData, Mutex, ObjectPoolAllocator<mm::ExtraObjectData>>;
public:
    class ThreadQueue : public Queue::Producer {
    public:
        explicit ThreadQueue(ExtraObjectDataFactory& registry) : Producer(registry.extraObjects_) {}

        mm::ExtraObjectData& Create(ObjHeader* baseObject, TypeInfo* typeInfo) noexcept {
            return **Emplace(baseObject, typeInfo);
        }

        void Destroy(mm::ExtraObjectData& data) noexcept {
            Erase(&Queue::Node::fromValue(data));
        }

        // Do not add fields as this is just a wrapper and Producer does not have virtual destructor.
    };

    using Iterator = Queue::Iterator;

    class Iterable : private MoveOnly {
    public:
        explicit Iterable(Queue::Iterable impl) noexcept : impl_(std::move(impl)) {}

        Iterator begin() noexcept { return impl_.begin(); }
        Iterator end() noexcept { return impl_.begin(); }

        void ApplyDeletions() noexcept {
            impl_.ApplyDeletions();
        }

        void Sweep(gc::GCHandle gcHandle) noexcept {
            auto sweepHandle = gcHandle.sweepExtraObjects();
            ApplyDeletions();
            for (auto it = begin(); it != end();) {
                auto &extraObject = *it;
                if (extraObject.getFlag(mm::ExtraObjectData::FLAGS_IN_FINALIZER_QUEUE)) {
                    ++it;
                    continue;
                }
                auto* baseObject = extraObject.GetBaseObject();
                if (!baseObject->heap() || gc::GC::isMarked(baseObject)) {
                    ++it;
                    continue;
                }
                extraObject.ClearWeakReferenceCounter();
                if (extraObject.HasAssociatedObject()) {
                    extraObject.DetachAssociatedObject();
                    extraObject.setFlag(mm::ExtraObjectData::FLAGS_IN_FINALIZER_QUEUE);
                    ++it;
                } else {
                    extraObject.Uninstall();
                    it.EraseAndAdvance();
                }
            }
        }

    private:
        Queue::Iterable impl_;
    };

    ExtraObjectDataFactory();
    ~ExtraObjectDataFactory();

    // Lock registry for safe iteration.
    Iterable LockForIter() noexcept { return Iterable(extraObjects_.LockForIter()); }

    void ClearForTests() noexcept { extraObjects_.ClearForTests(); }

    size_t GetSizeUnsafe() noexcept { return extraObjects_.GetSizeUnsafe(); }
    size_t GetTotalObjectsSizeUnsafe() noexcept { return extraObjects_.GetSizeUnsafe() * sizeof(mm::ExtraObjectData); }

private:
    Queue extraObjects_;
};

} // namespace kotlin::alloc
