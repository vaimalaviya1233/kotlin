/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "CustomAllocator.hpp"

#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cinttypes>
#include <new>

#include "CustomAllocConstants.hpp"
#include "CustomLogging.hpp"
#include "ExtraObjectData.hpp"
#include "ExtraObjectPage.hpp"
#include "GCApi.hpp"
#include "GCScheduler.hpp"
#include "LargePage.hpp"
#include "MediumPage.hpp"
#include "Memory.h"
#include "SmallPage.hpp"
#include "TypeInfo.h"
#include "Types.h"

namespace kotlin::alloc {

CustomAllocator::CustomAllocator(Heap& heap, gcScheduler::GCSchedulerThreadData& gcScheduler) noexcept :
    heap_(heap), gcScheduler_(gcScheduler), mediumPage_(nullptr), extraObjectPage_(nullptr) {
    CustomAllocInfo("CustomAllocator::CustomAllocator(heap)");
    memset(smallPages_, 0, sizeof(smallPages_));
}

ObjHeader* CustomAllocator::CreateObject(const TypeInfo* typeInfo) noexcept {
    RuntimeAssert(!typeInfo->IsArray(), "Must not be an array");
    size_t allocSize = ObjectAllocatedDataSize(typeInfo);
    auto* object = ObjectFromObjectData(Allocate(allocSize));
    if (typeInfo->flags_ & TF_HAS_FINALIZER) {
        auto* extraObject = CreateExtraObject();
        object->typeInfoOrMeta_ = reinterpret_cast<TypeInfo*>(new (extraObject) mm::ExtraObjectData(object, typeInfo));
    } else {
        object->typeInfoOrMeta_ = const_cast<TypeInfo*>(typeInfo);
    }
    return object;
}

ArrayHeader* CustomAllocator::CreateArray(const TypeInfo* typeInfo, uint32_t count) noexcept {
    RuntimeAssert(typeInfo->IsArray(), "Must be an array");
    auto allocSize = ArrayAllocatedDataSize(typeInfo, count);
    auto* array = ObjectFromObjectData(Allocate(allocSize))->array();
    array->typeInfoOrMeta_ = const_cast<TypeInfo*>(typeInfo);
    array->count_ = count;
    return array;
}

mm::ExtraObjectData* CustomAllocator::CreateExtraObject() noexcept {
    CustomAllocDebug("CustomAllocator::CreateExtraObject()");
    ExtraObjectPage* page = extraObjectPage_;
    if (page) {
        mm::ExtraObjectData* block = page->TryAllocate();
        if (block) {
            memset(block, 0, sizeof(mm::ExtraObjectData));
            return block;
        }
    }
    CustomAllocDebug("Failed to allocate in current ExtraObjectPage");
    while ((page = heap_.GetExtraObjectPage())) {
        mm::ExtraObjectData* block = page->TryAllocate();
        if (block) {
            extraObjectPage_ = page;
            memset(block, 0, sizeof(mm::ExtraObjectData));
            return block;
        }
    }
    return nullptr;
}

mm::ExtraObjectData& CustomAllocator::CreateExtraObject(ObjHeader* baseObject, const TypeInfo* info) noexcept {
    mm::ExtraObjectData* extraObject = CreateExtraObject();
    return *new (extraObject) mm::ExtraObjectData(baseObject, info);
}

void CustomAllocator::PrepareForGC() noexcept {
    CustomAllocInfo("CustomAllocator@%p::PrepareForGC()", this);
    mediumPage_ = nullptr;
    memset(smallPages_, 0, sizeof(smallPages_));
    extraObjectPage_ = nullptr;
}

uint8_t* CustomAllocator::Allocate(uint64_t size) noexcept {
    gcScheduler_.OnSafePointAllocation(size);
    CustomAllocDebug("CustomAllocator::Allocate(%" PRIu64 ")", size);
    uint64_t cellCount = (size + sizeof(Cell) - 1) / sizeof(Cell);
    uint8_t* ptr;
    if (cellCount <= SMALL_PAGE_MAX_BLOCK_SIZE) {
        ptr = AllocateInSmallPage(cellCount);
    } else if (cellCount > MEDIUM_PAGE_MAX_BLOCK_SIZE) {
        ptr = AllocateInLargePage(cellCount);
    } else {
        ptr = AllocateInMediumPage(cellCount);
    }
    memset(ptr, 0, size);
    return ptr;
}

uint8_t* CustomAllocator::AllocateInLargePage(uint64_t cellCount) noexcept {
    CustomAllocDebug("CustomAllocator::AllocateInLargePage(%" PRIu64 ")", cellCount);
    uint8_t* block = heap_.GetLargePage(cellCount)->TryAllocate();
    return block;
}

uint8_t* CustomAllocator::AllocateInMediumPage(uint32_t cellCount) noexcept {
    CustomAllocDebug("CustomAllocator::AllocateInMediumPage(%u)", cellCount);
    if (mediumPage_) {
        uint8_t* block = mediumPage_->TryAllocate(cellCount);
        if (block) return block;
    }
    CustomAllocDebug("Failed to allocate in curPage");
    while (true) {
        mediumPage_ = heap_.GetMediumPage(cellCount);
        uint8_t* block = mediumPage_->TryAllocate(cellCount);
        if (block) return block;
    }
}

uint8_t* CustomAllocator::AllocateInSmallPage(uint32_t cellCount) noexcept {
    CustomAllocDebug("CustomAllocator::AllocateInSmallPage(%u)", cellCount);
    SmallPage* page = smallPages_[cellCount];
    if (page) {
        uint8_t* block = page->TryAllocate();
        if (block) return block;
    }
    CustomAllocDebug("Failed to allocate in current SmallPage");
    while ((page = heap_.GetSmallPage(cellCount))) {
        uint8_t* block = page->TryAllocate();
        if (block) {
            smallPages_[cellCount] = page;
            return block;
        }
    }
    return nullptr;
}

} // namespace kotlin::alloc
