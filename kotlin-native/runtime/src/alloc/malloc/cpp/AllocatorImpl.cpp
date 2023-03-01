/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "AllocatorImpl.hpp"

using namespace kotlin;

alloc::Allocator::ThreadData::ThreadData(Allocator& owner, gcScheduler::GCSchedulerThreadData& gcScheduler) noexcept : impl_(std_support::make_unique<Impl>(owner.impl(), gcScheduler)) {}

alloc::Allocator::ThreadData::~ThreadData() = default;

void alloc::Allocator::ThreadData::publish() noexcept {
  impl().extraObjectDataFactory().Publish();
  impl().objectFactory().Publish();
}

void alloc::Allocator::ThreadData::clearForTests() noexcept {
  impl().extraObjectDataFactory().ClearForTests();
  impl().objectFactory().ClearForTests();
}

ObjHeader* alloc::Allocator::ThreadData::allocateObject(const TypeInfo* typeInfo) noexcept {
  return impl().objectFactory().CreateObject(typeInfo);
}

ArrayHeader* alloc::Allocator::ThreadData::allocateArray(const TypeInfo* typeInfo, uint32_t elements) noexcept {
  return impl().objectFactory().CreateArray(typeInfo, elements);
}

mm::ExtraObjectData& alloc::Allocator::ThreadData::allocateExtraObjectDataForObject(ObjHeader* header, TypeInfo* typeInfo) noexcept {
  return impl().extraObjectDataFactory().Create(header, typeInfo);
}

void alloc::Allocator::ThreadData::destroyExtraObjectData(mm::ExtraObjectData& data) noexcept {
  impl().extraObjectDataFactory().Destroy(data);
}

void alloc::Allocator::ThreadData::destroyExtraObjectData2(mm::ExtraObjectData& data) noexcept {
  impl().extraObjectDataFactory().Destroy(data);
}

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
  return ObjectFactory<ObjectFactoryTraits>::GetAllocatedHeapSize(object);
}

size_t alloc::Allocator::GetHeapObjectsCountUnsafe() noexcept {
  return impl().objectFactory().GetObjectsCountUnsafe();
}

size_t alloc::Allocator::GetTotalHeapObjectsSizeUnsafe() noexcept {
  return impl().objectFactory().GetTotalObjectsSizeUnsafe();
}

size_t alloc::Allocator::GetExtraObjectsCountUnsafe() noexcept {
  return impl().extraObjectDataFactory().GetSizeUnsafe();
}

size_t alloc::Allocator::GetTotalExtraObjectsSizeUnsafe() noexcept {
  return impl().extraObjectDataFactory().GetTotalObjectsSizeUnsafe();
}

// static
ObjHeader* alloc::Allocator::objectForData(void* data) noexcept {
    return Impl::ObjectFactory::NodeRef::FromObjectData(data)->GetObjHeader();
}

// static
void* alloc::Allocator::dataForObject(ObjHeader* object) noexcept {
    return Impl::ObjectFactory::NodeRef::FromObject(object)->ObjectData();
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
