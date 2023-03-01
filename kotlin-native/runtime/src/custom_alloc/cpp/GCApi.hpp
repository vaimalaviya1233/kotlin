/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef CUSTOM_ALLOC_CPP_GCAPI_HPP_
#define CUSTOM_ALLOC_CPP_GCAPI_HPP_

#include <cstdint>
#include <inttypes.h>
#include <limits>
#include <stdlib.h>

#include "Alignment.hpp"
#include "AtomicStack.hpp"
#include "ExtraObjectPage.hpp"
#include "GC.hpp"

namespace kotlin::alloc {

bool TryResetMark(void* ptr) noexcept;

// Returns true if swept successfully, i.e., if the extraobject can be reclaimed now.
bool SweepExtraObject(ExtraObjectCell* extraObjectCell, AtomicStack<ExtraObjectCell>& finalizerQueue) noexcept;

void* SafeAlloc(uint64_t size) noexcept;

inline size_t ObjectAllocatedDataSize(const TypeInfo* typeInfo) noexcept {
    return AlignUp(AlignUp(gc::GC::objectDataSize, kObjectAlignment) + typeInfo->instanceSize_, std::max(gc::GC::objectDataAlignment, kObjectAlignment));
}

inline uint64_t ArrayAllocatedDataSize(const TypeInfo* typeInfo, uint32_t count) noexcept {
    // -(int32_t min) * uint32_t max cannot overflow uint64_t. And are capped
    // at about half of uint64_t max.
    uint64_t membersSize = static_cast<uint64_t>(-typeInfo->instanceSize_) * count;
    return AlignUp<uint64_t>(AlignUp(gc::GC::objectDataSize, kObjectAlignment) + sizeof(ArrayHeader) + membersSize, std::max(gc::GC::objectDataAlignment, kObjectAlignment));
}

inline ObjHeader* ObjectFromObjectData(void* data) noexcept {
    return reinterpret_cast<ObjHeader*>(static_cast<uint8_t*>(data) + AlignUp(gc::GC::objectDataSize, kObjectAlignment));
}

inline void* ObjectDataFromObject(ObjHeader* object) noexcept {
    return reinterpret_cast<uint8_t*>(object) - AlignUp(gc::GC::objectDataSize, kObjectAlignment);
}

} // namespace kotlin::alloc

#endif
