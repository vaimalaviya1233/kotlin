/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCImpl.hpp"

#include "Common.h"
#include "GC.hpp"
#include "NoOpGC.hpp"
#include "std_support/Memory.hpp"
#include "GlobalData.hpp"
#include "GCStatistics.hpp"

using namespace kotlin;

gc::GC::ThreadData::ThreadData(GC& gc, mm::ThreadData& threadData) noexcept : impl_(std_support::make_unique<Impl>(gc, threadData)) {}

gc::GC::ThreadData::~ThreadData() = default;

ALWAYS_INLINE void gc::GC::ThreadData::SafePointFunctionPrologue() noexcept {
    impl_->gc().SafePointFunctionPrologue();
}

ALWAYS_INLINE void gc::GC::ThreadData::SafePointLoopBody() noexcept {
    impl_->gc().SafePointLoopBody();
}

void gc::GC::ThreadData::Schedule() noexcept {
    impl_->gc().Schedule();
}

void gc::GC::ThreadData::ScheduleAndWaitFullGC() noexcept {
    impl_->gc().ScheduleAndWaitFullGC();
}

void gc::GC::ThreadData::ScheduleAndWaitFullGCWithFinalizers() noexcept {
    impl_->gc().ScheduleAndWaitFullGCWithFinalizers();
}

void gc::GC::ThreadData::Publish() noexcept {
#ifndef CUSTOM_ALLOCATOR
    impl_->objectFactoryThreadQueue().Publish();
#endif
}

void gc::GC::ThreadData::ClearForTests() noexcept {
#ifndef CUSTOM_ALLOCATOR
    impl_->objectFactoryThreadQueue().ClearForTests();
#endif
}

ALWAYS_INLINE ObjHeader* gc::GC::ThreadData::CreateObject(const TypeInfo* typeInfo) noexcept {
#ifndef CUSTOM_ALLOCATOR
    return impl_->objectFactoryThreadQueue().CreateObject(typeInfo);
#else
    return impl_->alloc().CreateObject(typeInfo);
#endif
}

ALWAYS_INLINE ArrayHeader* gc::GC::ThreadData::CreateArray(const TypeInfo* typeInfo, uint32_t elements) noexcept {
#ifndef CUSTOM_ALLOCATOR
    return impl_->objectFactoryThreadQueue().CreateArray(typeInfo, elements);
#else
    return impl_->alloc().CreateArray(typeInfo, elements);
#endif
}

void gc::GC::ThreadData::OnStoppedForGC() noexcept {
    impl_->gcScheduler().OnStoppedForGC();
}

void gc::GC::ThreadData::OnSuspendForGC() noexcept { }

gc::GC::GC() noexcept : impl_(std_support::make_unique<Impl>()) {}

gc::GC::~GC() = default;

// static
size_t gc::GC::GetAllocatedHeapSize(ObjHeader* object) noexcept {
#ifndef CUSTOM_ALLOCATOR
    return mm::ObjectFactory<GCImpl>::GetAllocatedHeapSize(object);
#else
    return alloc::CustomAllocator::GetAllocatedHeapSize(object);
#endif
}

size_t gc::GC::GetTotalHeapObjectsSizeBytes() const noexcept {
    return allocatedBytes();
}

gc::GCSchedulerConfig& gc::GC::gcSchedulerConfig() noexcept {
    return impl_->gcScheduler().config();
}

void gc::GC::ClearForTests() noexcept {
#ifndef CUSTOM_ALLOCATOR
    impl_->objectFactory().ClearForTests();
#endif
    GCHandle::ClearForTests();
}

void gc::GC::StartFinalizerThreadIfNeeded() noexcept {}

void gc::GC::StopFinalizerThreadIfRunning() noexcept {}

bool gc::GC::FinalizersThreadIsRunning() noexcept {
    return false;
}

// static
ALWAYS_INLINE void gc::GC::processObjectInMark(void* state, ObjHeader* object) noexcept {}

// static
ALWAYS_INLINE void gc::GC::processArrayInMark(void* state, ArrayHeader* array) noexcept {}

// static
ALWAYS_INLINE void gc::GC::processFieldInMark(void* state, ObjHeader* field) noexcept {}

// static
const size_t gc::GC::objectDataSize = 0; // sizeof(NoOpGC::ObjectData) with [[no_unique_address]]

// static
ALWAYS_INLINE bool gc::GC::SweepObject(void *objectData) noexcept {
    return true;
}
