/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "ObjectOps.hpp"

#include <atomic>

#include "Common.h"
#include "ExtraObjectData.hpp"
#include "ThreadData.hpp"
#include "ThreadState.hpp"

using namespace kotlin;

namespace {

using WeakRefReadType = ObjHeader*(*)(ObjHeader*, ObjHeader**) noexcept;

OBJ_GETTER(weakRefReadWithBarriers, ObjHeader* object) noexcept {
    if (!object) {
        RETURN_OBJ(nullptr);
    }
    // When weak ref barriers are on, `marked()` cannot change,
    // and ExtraObjectData cannot be gone.
    auto* extraObjectData = mm::ExtraObjectData::Get(object);
    RuntimeAssert(extraObjectData != nullptr, "For someone to have weak access, ExtraObjectData must've been created");
    if (!extraObjectData->marked()) {
        return nullptr;
    }
    RETURN_OBJ(object);
}

std::atomic<WeakRefReadType> weakRefReadImpl = mm::weakRefReadDefault;

}

// TODO: Memory barriers.

ALWAYS_INLINE void mm::SetStackRef(ObjHeader** location, ObjHeader* value) noexcept {
    AssertThreadState(ThreadState::kRunnable);
    *location = value;
}

ALWAYS_INLINE void mm::SetHeapRef(ObjHeader** location, ObjHeader* value) noexcept {
    AssertThreadState(ThreadState::kRunnable);
    *location = value;
}

#pragma clang diagnostic push
// On 32-bit android arm clang warns of significant performance penalty because of large
// atomic operations. TODO: Consider using alternative ways of ordering memory operations if they
// turn out to be more efficient on these platforms.
#pragma clang diagnostic ignored "-Watomic-alignment"

ALWAYS_INLINE void mm::SetHeapRefAtomic(ObjHeader** location, ObjHeader* value) noexcept {
    AssertThreadState(ThreadState::kRunnable);
    __atomic_store_n(location, value, __ATOMIC_RELEASE);
}

ALWAYS_INLINE OBJ_GETTER(mm::ReadHeapRefAtomic, ObjHeader** location) noexcept {
    AssertThreadState(ThreadState::kRunnable);
    // TODO: Make this work with GCs that can stop thread at any point.
    auto result = __atomic_load_n(location, __ATOMIC_ACQUIRE);
    RETURN_OBJ(result);
}

ALWAYS_INLINE OBJ_GETTER(mm::CompareAndSwapHeapRef, ObjHeader** location, ObjHeader* expected, ObjHeader* value) noexcept {
    AssertThreadState(ThreadState::kRunnable);
    // TODO: Make this work with GCs that can stop thread at any point.
    ObjHeader* actual = expected;
    // TODO: Do we need this strong memory model? Do we need to use strong CAS?
    // This intrinsic modifies `actual` non-atomically.
    __atomic_compare_exchange_n(location, &actual, value, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
    // On success, we already have old value (== `expected`) in `actual`.
    // On failure, we have the old value written into `actual`.
    RETURN_OBJ(actual);
}

#pragma clang diagnostic pop

OBJ_GETTER(mm::AllocateObject, ThreadData* threadData, const TypeInfo* typeInfo) noexcept {
    AssertThreadState(threadData, ThreadState::kRunnable);
    // TODO: Make this work with GCs that can stop thread at any point.
    auto* object = threadData->gc().CreateObject(typeInfo);
    RETURN_OBJ(object);
}

OBJ_GETTER(mm::AllocateArray, ThreadData* threadData, const TypeInfo* typeInfo, uint32_t elements) noexcept {
    AssertThreadState(threadData, ThreadState::kRunnable);
    // TODO: Make this work with GCs that can stop thread at any point.
    auto* array = threadData->gc().CreateArray(typeInfo, static_cast<uint32_t>(elements));
    // `ArrayHeader` and `ObjHeader` are expected to be compatible.
    RETURN_OBJ(reinterpret_cast<ObjHeader*>(array));
}

size_t mm::GetAllocatedHeapSize(ObjHeader* object) noexcept {
    return gc::GC::GetAllocatedHeapSize(object);
}

OBJ_GETTER(mm::weakRefRead, ObjHeader* object) noexcept {
    // weakRefReadImpl only changes inside STW. Access is always synchronized.
    auto* impl = weakRefReadImpl.load(std::memory_order_relaxed);
    RETURN_RESULT_OF(impl, object);
}

OBJ_GETTER(mm::weakRefReadDefault, ObjHeader* object) noexcept {
    RETURN_OBJ(object);
}

void mm::enableWeakRefBarriers() noexcept {
    // Happens inside STW.
    weakRefReadImpl.store(weakRefReadWithBarriers, std::memory_order_relaxed);
}

void mm::disableWeakRefBarriers() noexcept {
    // Happens inside STW.
    weakRefReadImpl.store(mm::weakRefReadDefault, std::memory_order_relaxed);
}
