/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "WeakRefBarriers.hpp"

#include "ExtraObjectData.hpp"

#include <atomic>

using namespace kotlin;

namespace {

using WeakRefReadType = ObjHeader*(*)(ObjHeader*, ObjHeader**) noexcept;

OBJ_GETTER(weakRefReadNoBarriers, ObjHeader* object) noexcept {
    RETURN_OBJ(object);
}

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

std::atomic<WeakRefReadType> weakRefReadImpl = weakRefReadNoBarriers;

}

void gc::enableWeakRefBarriers() noexcept {
    // Happens inside STW.
    weakRefReadImpl.store(weakRefReadWithBarriers, std::memory_order_relaxed);
}

void gc::disableWeakRefBarriers() noexcept {
    // Happens inside STW.
    weakRefReadImpl.store(weakRefReadNoBarriers, std::memory_order_relaxed);
}

OBJ_GETTER(gc::weakRefRead, ObjHeader* object) noexcept {
    // weakRefReadImpl only changes inside STW. Access is always synchronized.
    auto* impl = weakRefReadImpl.load(std::memory_order_relaxed);
    RETURN_RESULT_OF(impl, object);
}
