/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCSchedulerImpl.hpp"

#include "Logging.hpp"

using namespace kotlin;

gcScheduler::GCScheduler::GCScheduler(gc::GC& gc) noexcept : impl_(std_support::make_unique<Impl>(gc)) {
    RuntimeLogDebug({kTagGC}, "Initialized manual GC scheduler");
}

gcScheduler::GCScheduler::~GCScheduler() = default;

void gcScheduler::GCScheduler::schedule() noexcept {
    impl_->schedule();
}

void gcScheduler::GCScheduler::scheduleAndWaitFullGC() noexcept {
    impl_->scheduleAndWaitFullGC();
}

void gcScheduler::GCScheduler::scheduleAndWaitFullGCWithFinalizers() noexcept {
    impl_->scheduleAndWaitFullGCWithFinalizers();
}

void gcScheduler::GCScheduler::onAllocation(size_t allocatedBytes) noexcept {}
void gcScheduler::GCScheduler::onOOM(size_t size) noexcept {}
void gcScheduler::GCScheduler::onSafePoint() noexcept {}
