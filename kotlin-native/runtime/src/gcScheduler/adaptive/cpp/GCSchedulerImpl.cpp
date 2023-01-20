/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCSchedulerImpl.hpp"

using namespace kotlin;

gcScheduler::GCScheduler::GCScheduler(gc::GC& gc) noexcept : impl_(std_support::make_unique<Impl>(*this, config_, gc)) {
    RuntimeLogDebug({kTagGC}, "Initialized adaptive GC scheduler");
}

gcScheduler::GCScheduler::~GCScheduler() = default;

void gcScheduler::GCScheduler::schedule() noexcept {
    impl_->impl().schedule();
}

void gcScheduler::GCScheduler::scheduleAndWaitFullGC() noexcept {
    impl_->impl().scheduleAndWaitFullGC();
}

void gcScheduler::GCScheduler::scheduleAndWaitFullGCWithFinalizers() noexcept {
    impl_->impl().scheduleAndWaitFullGCWithFinalizers();
}

void gcScheduler::GCScheduler::onAllocation(uint64_t allocatedBytes) noexcept {
    impl_->impl().onAllocation(allocatedBytes);
}

void gcScheduler::GCScheduler::onOOM(uint64_t size) noexcept {
    impl_->impl().onOOM(size);
}

void gcScheduler::GCScheduler::onSafePoint() noexcept {}
