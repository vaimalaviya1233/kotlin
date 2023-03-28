/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "ForeignRefRegistry.hpp"

#include "GlobalData.hpp"

using namespace kotlin;

// static
mm::ForeignRefRegistry& mm::ForeignRefRegistry::instance() noexcept {
    return mm::GlobalData::Instance().foreignRefRegistry();
}

mm::ForeignRefRegistry::Record* mm::ForeignRefRegistry::nextRoot(Record* current) noexcept {
    RuntimeAssert(current != nullptr, "current cannot be null");
    RuntimeAssert(current != rootsTail(), "current cannot be tail");
    Record* candidate = current->next_.load(std::memory_order_relaxed);
    while (true) {
        RuntimeAssert(current != nullptr, "current cannot be null");
        RuntimeAssert(current != rootsTail(), "current cannot be tail");
        RuntimeAssert(candidate != nullptr, "candidate cannot be null");
        if (candidate == rootsTail())
            // Reached tail, nothing to do anymore
            return candidate;
        if (candidate->isReferenced()) {
            // Keeping acquire-release for next_.
            std::atomic_thread_fence(std::memory_order_acquire);
            // Perfectly good node. Stop right there.
            return candidate;
        }
        // Bad node. Let's remove it from the roots.
        // Racy if someone concurrently inserts in the middle. Or iterates.
        // But we don't have that here. Inserts are only in the beginning.
        // Iteration also happens only here.
        auto [candidatePrev, candidateNext] = eraseFromRoots(current, candidate);
        // We removed candidate. But should we have?
        if (candidate->isReferenced()) {
            // Ooops. Let's put it back. Okay to put into the head.
            insertIntoRootsHead(candidate);
        }
        // eraseFromRoots and insertIntoRootsHead are both acquire-release fences.
        // This means they play nice with each other and we don't need an extra fence
        // here to ensure synchronization with 0->1 BackRefFromAssociatedObject::refCount change:
        // * We read refCount after eraseFromRoots.
        // * retainRef writes refCount before insertIntoRootsHead.
        // So the write to rc_ in retainRef happens before the read here.
        //
        // Okay, properly deleted. Our new `candidate` is the next of previous candidate,
        // and our `current` then is our best guess at the previous node of the `candidate`.
        current = candidatePrev;
        candidate = candidateNext;
        // `current` has either moved forward or stayed where it is.
        // `candidate` has definitely moved forward.
        // `current` is only used in `eraseFromRoots` which itself ensures that no
        // infinite loop can happen.
        // So, this loop is also not infinite.
    }
}

std::pair<mm::ForeignRefRegistry::Record*, mm::ForeignRefRegistry::Record*> mm::ForeignRefRegistry::eraseFromRoots(
        Record* prev, Record* record) noexcept {
    RuntimeAssert(prev != rootsTail(), "prev cannot be tail");
    RuntimeAssert(record != rootsHead(), "record cannot be head");
    RuntimeAssert(record != rootsTail(), "record cannot be tail");
    Record* next = record->next_.load(std::memory_order_acquire);
    RuntimeAssert(next != nullptr, "record's next cannot be null");
    do {
        Record* prevExpectedNext = record;
        bool removed = prev->next_.compare_exchange_strong(prevExpectedNext, next, std::memory_order_release, std::memory_order_acquire);
        if (removed) {
            Record* actualNext = record->next_.exchange(nullptr, std::memory_order_acq_rel);
            RuntimeAssert(next == actualNext, "Broken Record@%p removal. Expected next %p actual %p", record, next, actualNext);
            return {prev, next};
        }
        prev = prevExpectedNext;
        RuntimeAssert(prev != rootsHead(), "prev cannot be head");
        RuntimeAssert(prev != rootsTail(), "prev cannot be tail");
        // We moved `prev` forward, nothing can insert after `prev` anymore, this
        // cannot be an infinite loop, then.
    } while (true);
}

void mm::ForeignRefRegistry::insertIntoRootsHead(Record* record) noexcept {
    Record* next = rootsHead()->next_.load(std::memory_order_acquire);
    Record* recordExpectedNext = nullptr;
    do {
        RuntimeAssert(next != nullptr, "head's next cannot be null");
        if (!record->next_.compare_exchange_strong(recordExpectedNext, next, std::memory_order_release, std::memory_order_acquire)) {
            // So:
            // * `record` is already in the roots list
            // * some other thread is inserting it in the roots list
            // * GC thread may be removing it from the roots list, but
            //   will recheck rc afterwards and insert it back if needed
            // In either case, do not touch anything anymore here.
            return;
        }
        // CAS was successfull, so we need to update the expected value of record.next_
        recordExpectedNext = next;
    } while (!rootsHead()->next_.compare_exchange_weak(next, record, std::memory_order_release, std::memory_order_acquire));
}
