/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "GC.hpp"

#include "ConcurrentMarkAndSweep.hpp"

namespace kotlin {
namespace gc {

using GCImpl = ConcurrentMarkAndSweep;

class GC::Impl : private Pinned {
public:
    Impl(gcScheduler::GCScheduler& gcScheduler, alloc::Allocator& allocator) noexcept : gc_(gcScheduler, allocator) {}

    GCImpl& gc() noexcept { return gc_; }

private:
    GCImpl gc_;
};

class GC::ThreadData::Impl : private Pinned {
public:
    Impl(GC& gc, gcScheduler::GCSchedulerThreadData& gcScheduler, mm::ThreadData& threadData) noexcept :
        gc_(gc.impl_->gc(), threadData, gcScheduler) {}

    GCImpl::ThreadData& gc() noexcept { return gc_; }

private:
    GCImpl::ThreadData gc_;
};

} // namespace gc
} // namespace kotlin
