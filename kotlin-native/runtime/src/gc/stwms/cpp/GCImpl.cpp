/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCImpl.hpp"

#include "GC.hpp"
#include "MarkAndSweepUtils.hpp"
#include "std_support/Memory.hpp"
#include "GlobalData.hpp"
#include "GCStatistics.hpp"

using namespace kotlin;

// static
const size_t gc::GC::objectDataSize = sizeof(StopTheWorldMarkAndSweep::ObjectData);

// static
const size_t gc::GC::objectDataAlignment = alignof(StopTheWorldMarkAndSweep::ObjectData);

gc::GC::ThreadData::ThreadData(GC& gc, gcScheduler::GCSchedulerThreadData& gcScheduler, mm::ThreadData& threadData) noexcept :
    impl_(std_support::make_unique<Impl>(gc, gcScheduler, threadData)) {}

gc::GC::ThreadData::~ThreadData() = default;

ALWAYS_INLINE void gc::GC::ThreadData::SafePointFunctionPrologue() noexcept {
    mm::SuspendIfRequested();
}

ALWAYS_INLINE void gc::GC::ThreadData::SafePointLoopBody() noexcept {
    mm::SuspendIfRequested();
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

void gc::GC::ThreadData::Publish() noexcept {}

void gc::GC::ThreadData::ClearForTests() noexcept {}

void gc::GC::ThreadData::OnSuspendForGC() noexcept {}

gc::GC::GC(gcScheduler::GCScheduler& gcScheduler, alloc::Allocator& allocator) noexcept : impl_(std_support::make_unique<Impl>(gcScheduler, allocator)) {}

gc::GC::~GC() = default;

void gc::GC::ClearForTests() noexcept {
    GCHandle::ClearForTests();
}

// static
bool gc::GC::isMarked(ObjHeader* object) noexcept {
    auto& objectData = *static_cast<StopTheWorldMarkAndSweep::ObjectData*>(alloc::Allocator::dataForObject(object));
    return objectData.marked();
}

// static
bool gc::GC::tryResetMark(ObjHeader* object) noexcept {
    auto& objectData = *static_cast<StopTheWorldMarkAndSweep::ObjectData*>(alloc::Allocator::dataForObject(object));
    return objectData.tryResetMark();
}

// static
void gc::GC::keepAlive(ObjHeader* object) noexcept {
    auto& objectData = *static_cast<StopTheWorldMarkAndSweep::ObjectData*>(alloc::Allocator::dataForObject(object));
    objectData.tryMark();
}

// static
ALWAYS_INLINE void gc::GC::processObjectInMark(void* state, ObjHeader* object) noexcept {
    gc::internal::processObjectInMark<gc::internal::MarkTraits>(state, object);
}

// static
ALWAYS_INLINE void gc::GC::processArrayInMark(void* state, ArrayHeader* array) noexcept {
    gc::internal::processArrayInMark<gc::internal::MarkTraits>(state, array);
}

// static
ALWAYS_INLINE void gc::GC::processFieldInMark(void* state, ObjHeader* field) noexcept {
    gc::internal::processFieldInMark<gc::internal::MarkTraits>(state, field);
}

void gc::GC::Schedule() noexcept {
    impl_->gc().Schedule();
}
