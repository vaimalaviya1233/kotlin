/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCSchedulerImpl.hpp"

using namespace kotlin;

gcScheduler::GCScheduler::GCScheduler(gc::GC& gc) noexcept : impl_(std_support::make_unique<Impl>(*this, config_, gc)) {
    RuntimeLogDebug({kTagGC}, "Initialized aggressive GC scheduler");
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

void gcScheduler::GCScheduler::onAllocation(size_t allocatedBytes) noexcept {
    impl_->onAllocation(allocatedBytes);
}

void gcScheduler::GCScheduler::onOOM(size_t size) noexcept {
    impl_->onOOM(size);
}

void gcScheduler::GCScheduler::onSafePoint() noexcept {
    impl_->onSafePoint();
}
