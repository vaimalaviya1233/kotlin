/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCApi.hpp"

#include <limits>

#include "CustomLogging.hpp"
#include "FinalizerHooks.hpp"
#include "GC.hpp"
#include "KAssert.h"

namespace kotlin::alloc {

bool TryResetMark(void* ptr) noexcept {
    return gc::GC::tryResetMark(ObjectFromObjectData(ptr));
}

static void KeepAlive(ObjHeader* baseObject) noexcept {
    gc::GC::keepAlive(baseObject);
}

static bool IsAlive(ObjHeader* baseObject) noexcept {
    return gc::GC::isMarked(baseObject);
}

bool SweepExtraObject(ExtraObjectCell* extraObjectCell, AtomicStack<ExtraObjectCell>& finalizerQueue) noexcept {
    auto* extraObject = extraObjectCell->Data();
    if (extraObject->getFlag(mm::ExtraObjectData::FLAGS_FINALIZED)) {
        CustomAllocDebug("SweepIsCollectable(%p): already finalized", extraObject);
        return true;
    }
    auto* baseObject = extraObject->GetBaseObject();
    RuntimeAssert(baseObject->heap(), "SweepIsCollectable on a non-heap object");
    if (extraObject->getFlag(mm::ExtraObjectData::FLAGS_IN_FINALIZER_QUEUE)) {
        CustomAllocDebug("SweepIsCollectable(%p): already in finalizer queue, keep base object (%p) alive", extraObject, baseObject);
        KeepAlive(baseObject);
        return false;
    }
    if (IsAlive(baseObject)) {
        CustomAllocDebug("SweepIsCollectable(%p): base object (%p) is alive", extraObject, baseObject);
        return false;
    }
    extraObject->ClearWeakReferenceCounter();
    if (extraObject->HasAssociatedObject()) {
        extraObject->DetachAssociatedObject();
        extraObject->setFlag(mm::ExtraObjectData::FLAGS_IN_FINALIZER_QUEUE);
        finalizerQueue.Push(extraObjectCell);
        KeepAlive(baseObject);
        CustomAllocDebug("SweepIsCollectable(%p): add to finalizerQueue", extraObject);
        return false;
    } else {
        if (HasFinalizers(baseObject)) {
            extraObject->setFlag(mm::ExtraObjectData::FLAGS_IN_FINALIZER_QUEUE);
            finalizerQueue.Push(extraObjectCell);
            KeepAlive(baseObject);
            CustomAllocDebug("SweepIsCollectable(%p): addings to finalizerQueue, keep base object (%p) alive", extraObject, baseObject);
            return false;
        }
        extraObject->Uninstall();
        CustomAllocDebug("SweepIsCollectable(%p): uninstalled extraObject", extraObject);
        return true;
    }
}

void* SafeAlloc(uint64_t size) noexcept {
    void* memory;
    if (size > std::numeric_limits<size_t>::max() || !(memory = std_support::malloc(size))) {
        konan::consoleErrorf("Out of memory trying to allocate %" PRIu64 "bytes. Aborting.\n", size);
        konan::abort();
    }
    return memory;
}

} // namespace kotlin::alloc
