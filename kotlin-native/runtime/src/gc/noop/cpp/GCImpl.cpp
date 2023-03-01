/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCImpl.hpp"

#include "GC.hpp"
#include "std_support/Memory.hpp"
#include "GlobalData.hpp"
#include "GCStatistics.hpp"

using namespace kotlin;

namespace {
struct ObjectData {};
}

// static
const size_t gc::GC::objectDataSize = sizeof(ObjectData);

// static
const size_t gc::GC::objectDataAlignment = alignof(ObjectData);

gc::GC::ThreadData::ThreadData(GC& gc, gcScheduler::GCSchedulerThreadData&, mm::ThreadData& threadData) noexcept :
    impl_(std_support::make_unique<Impl>()) {}

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
}

void gc::GC::ThreadData::ClearForTests() noexcept {
}

void gc::GC::ThreadData::OnSuspendForGC() noexcept { }

gc::GC::GC(gcScheduler::GCScheduler&, alloc::Allocator& allocator) noexcept : impl_(std_support::make_unique<Impl>()) {}

gc::GC::~GC() = default;

void gc::GC::ClearForTests() noexcept {
    GCHandle::ClearForTests();
}

// static
bool gc::GC::isMarked(ObjHeader* object) noexcept {
    return true;
}

// static
bool gc::GC::tryResetMark(ObjHeader* object) noexcept {
    return true;
}

// static
void gc::GC::keepAlive(ObjHeader* object) noexcept {}

// static
ALWAYS_INLINE void gc::GC::processObjectInMark(void* state, ObjHeader* object) noexcept {}

// static
ALWAYS_INLINE void gc::GC::processArrayInMark(void* state, ArrayHeader* array) noexcept {}

// static
ALWAYS_INLINE void gc::GC::processFieldInMark(void* state, ObjHeader* field) noexcept {}

void gc::GC::Schedule() noexcept {}
