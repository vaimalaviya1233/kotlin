/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <atomic>

#include "Memory.h"
#include "MemorySharedRefs.hpp"
#include "MultiSourceQueue.hpp"
#include "ThreadRegistry.hpp"

namespace kotlin {
namespace mm {

// Registry for all objects that have foreign references created for them (i.e. associated objects)
class ForeignRefRegistry : Pinned {
    using Mutex = SpinLock<MutexThreadStateHandling::kIgnore>;

public:
    class Record {
    public:
        explicit Record(BackRefFromAssociatedObject* owner) noexcept :
            owner_(owner),
            obj_(owner->refUnsafe()) {
            RuntimeAssert(owner != nullptr, "Creating Record@%p with null owner", this);
            obj_ = owner->refUnsafe();
        }

        ~Record() {
            if (compiler::runtimeAssertsEnabled()) {
                auto* owner = owner_.load(std::memory_order_relaxed);
                RuntimeAssert(owner == nullptr, "Record@%p is attached to owner %p during destruction", this, owner);
                auto* next = next_.load(std::memory_order_relaxed);
                RuntimeAssert(next == nullptr, "Record@%p is inside roots list with next %p during destruction", this, next);
            }
        }

        void deinit() noexcept {
            // This happens during weak references invalidation.
            // If the Record was inside roots list, the corresponding object must have been
            // marked, and so couldn't be deinited.
            owner_.store(nullptr, std::memory_order_relaxed);
            if (compiler::runtimeAssertsEnabled()) {
                auto* next = next_.load(std::memory_order_relaxed);
                RuntimeAssert(next == nullptr, "Record@%p is inside roots list with next %p during deinit", this, next);
            }
        }

        void promote() noexcept {
            // TODO: With CMS barrier for marking object should be here.
            ForeignRefRegistry::instance().insertIntoRootsHead(this);
        }

        bool canBeSwept() const noexcept {
            // This happens during foreign refs sweeping.
            if (owner_.load(std::memory_order_relaxed) != nullptr)
                return false;
            if (compiler::runtimeAssertsEnabled()) {
                auto* next = next_.load(std::memory_order_relaxed);
                RuntimeAssert(next == nullptr, "Record@%p is inside roots list with next %p during foreign refs sweeping", this, next);
            }
            return true;
        }

        bool isReferenced() const noexcept {
            // This happens during roots scanning.
            // The owner can only be detached during weak references processing,
            // which cannot run concurrently with root scanning.
            auto* owner = owner_.load(std::memory_order_relaxed);
            return owner && !owner->isUnreferenced();
        }

        ObjHeader* refForTests() const noexcept {
            return obj_;
        }

    private:
        friend class ForeignRefRegistry;

        std::atomic<BackRefFromAssociatedObject*> owner_ = nullptr;
        ObjHeader* obj_ = nullptr;
        std::atomic<Record*> next_ = nullptr;
    };

    using Node = MultiSourceQueue<Record, Mutex>::Node;

    class ThreadQueue : Pinned {
    public:
        explicit ThreadQueue(ForeignRefRegistry& owner) noexcept : impl_(owner.impl_) {}

        Node* initForeignRef(BackRefFromAssociatedObject* backRef) noexcept {
            return impl_.Emplace(backRef);
        }

        void publish() noexcept {
            impl_.Publish();
        }

        void clearForTests() noexcept {
            impl_.ClearForTests();
        }

    private:
        MultiSourceQueue<Record, Mutex>::Producer impl_;
    };

    class RootsIterator {
    public:
        ObjHeader* operator*() noexcept {
            return node_->obj_;
        }

        RootsIterator& operator++() noexcept {
            node_ = owner_->nextRoot(node_);
            return *this;
        }

        bool operator==(const RootsIterator& rhs) const noexcept {
            return node_ == rhs.node_;
        }

        bool operator!=(const RootsIterator& rhs) const noexcept {
            return !(*this == rhs);
        }

    private:
        friend class ForeignRefRegistry;

        RootsIterator(ForeignRefRegistry& owner, Record* node) noexcept : owner_(&owner), node_(node) {}

        ForeignRefRegistry* owner_;
        Record* node_;
    };

    class RootsIterable : private MoveOnly {
    public:
        RootsIterator begin() noexcept {
            return RootsIterator(owner_, owner_.nextRoot(owner_.rootsHead()));
        }

        RootsIterator end() noexcept {
            return RootsIterator(owner_, owner_.rootsTail());
        }

    private:
        friend class ForeignRefRegistry;

        explicit RootsIterable(ForeignRefRegistry& owner) noexcept : owner_(owner) {}

        ForeignRefRegistry& owner_;
    };

    using Iterable = MultiSourceQueue<Record, Mutex>::Iterable;
    using Iterator = MultiSourceQueue<Record, Mutex>::Iterator;

    static ForeignRefRegistry& instance() noexcept;

    ForeignRefRegistry() noexcept {
        rootsHead()->next_.store(rootsTail(), std::memory_order_relaxed);
    }

    ~ForeignRefRegistry() = default;

    RootsIterable roots() noexcept { return RootsIterable(*this); }

    // Lock registry for safe iteration.
    // TODO: Iteration over `impl_` will be slow, because it's `std_support::list` collected at different times from
    // different threads, and so the nodes are all over the memory. Use metrics to understand how
    // much of a problem is it.
    Iterable lockForIter() noexcept { return impl_.LockForIter(); }

    void clearForTests() noexcept { impl_.ClearForTests(); }

private:
    Record* nextRoot(Record* record) noexcept;
    // Erase `record` from the roots list. `prev` is the current guess of the `record`
    // predecessor. Returns two nodes between which `record` was deleted.
    std::pair<Record*, Record*> eraseFromRoots(Record* prev, Record* record) noexcept;
    void insertIntoRootsHead(Record* record) noexcept;

    Record* rootsHead() noexcept { return reinterpret_cast<Record*>(rootsHeadStorage_); }
    const Record* rootsHead() const noexcept { return reinterpret_cast<const Record*>(rootsHeadStorage_); }
    static Record* rootsTail() noexcept { return reinterpret_cast<Record*>(rootsTailStorage_); }

    MultiSourceQueue<Record, Mutex> impl_;
    // TODO: See if intrusive_forward_list can be adapted for this.
    alignas(Record) char rootsHeadStorage_[sizeof(Record)] = {0};
    alignas(Record) static inline char rootsTailStorage_[sizeof(Record)] = {0};
};

} // namespace mm
} // namespace kotlin
