/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "AllocatorImpl.hpp"
#include "GCApi.hpp"

using namespace kotlin;

alloc::Allocator::ThreadData::ThreadData(Allocator& owner, gcScheduler::GCSchedulerThreadData& gcScheduler) noexcept : impl_(std_support::make_unique<Impl>(owner.impl(), gcScheduler)) {}

alloc::Allocator::ThreadData::~ThreadData() = default;

void alloc::Allocator::ThreadData::publish() noexcept {}

void alloc::Allocator::ThreadData::clearForTests() noexcept {}

ObjHeader* alloc::Allocator::ThreadData::allocateObject(const TypeInfo* typeInfo) noexcept {
  return impl().allocator().CreateObject(typeInfo);
}

ArrayHeader* alloc::Allocator::ThreadData::allocateArray(const TypeInfo* typeInfo, uint32_t elements) noexcept {
  return impl().allocator().CreateArray(typeInfo, elements);
}

mm::ExtraObjectData& alloc::Allocator::ThreadData::allocateExtraObjectDataForObject(ObjHeader* header, TypeInfo* typeInfo) noexcept {
  return impl().allocator().CreateExtraObject(header, typeInfo);
}

void alloc::Allocator::ThreadData::destroyExtraObjectData(mm::ExtraObjectData& data) noexcept {
  data.setFlag(mm::ExtraObjectData::FLAGS_FINALIZED);
}

void alloc::Allocator::ThreadData::destroyExtraObjectData2(mm::ExtraObjectData& data) noexcept {}

alloc::Allocator::GCContext::GCContext(Allocator& allocator, gc::GCHandle gcHandle) noexcept: impl_(std_support::make_unique<Impl>(gcHandle, allocator.impl())) {}

alloc::Allocator::GCContext::~GCContext() = default;

void alloc::Allocator::GCContext::sweepExtraObjects() noexcept {
    impl().sweepExtraObjects();
}

void alloc::Allocator::GCContext::sweep() noexcept {
    impl().sweep();
}

alloc::Allocator::Allocator() noexcept : impl_(std_support::make_unique<Impl>()) {}

alloc::Allocator::~Allocator() = default;

void alloc::Allocator::setFinalizerCompletion(std::function<void(int64_t)> f) noexcept {
    impl().finalizerCompletion() = std::move(f);
}

// static
size_t alloc::Allocator::GetAllocatedHeapSize(ObjHeader* object) noexcept {
    RuntimeAssert(object->heap(), "Object must be a heap object");
    const auto* typeInfo = object->type_info();
    if (typeInfo->IsArray()) {
        return ArrayAllocatedDataSize(typeInfo, object->array()->count_);
    } else {
        return ObjectAllocatedDataSize(typeInfo);
    }
}

size_t alloc::Allocator::GetHeapObjectsCountUnsafe() noexcept {
  // TODO: Fix
  return 0;
}

size_t alloc::Allocator::GetTotalHeapObjectsSizeUnsafe() noexcept {
  // TODO: Fix
  return 0;
}

size_t alloc::Allocator::GetExtraObjectsCountUnsafe() noexcept {
  // TODO: Fix
  return 0;
}

size_t alloc::Allocator::GetTotalExtraObjectsSizeUnsafe() noexcept {
  // TODO: Fix
  return 0;
}

// static
ObjHeader* alloc::Allocator::objectForData(void* data) noexcept {
    return ObjectFromObjectData(data);
}

// static
void* alloc::Allocator::dataForObject(ObjHeader* object) noexcept {
    return ObjectDataFromObject(object);
}

void alloc::Allocator::StartFinalizerThreadIfNeeded() noexcept {
    NativeOrUnregisteredThreadGuard guard(true);
    impl().finalizerProcessor().StartFinalizerThreadIfNone();
    impl().finalizerProcessor().WaitFinalizerThreadInitialized();
}

void alloc::Allocator::StopFinalizerThreadIfRunning() noexcept {
    NativeOrUnregisteredThreadGuard guard(true);
    impl().finalizerProcessor().StopFinalizerThread();
}

bool alloc::Allocator::FinalizersThreadIsRunning() noexcept {
    return impl().finalizerProcessor().IsRunning();
}

void alloc::Allocator::clearForTests() noexcept {
    StopFinalizerThreadIfRunning();
}
