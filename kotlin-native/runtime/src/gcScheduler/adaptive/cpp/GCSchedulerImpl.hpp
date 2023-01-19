/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "GCScheduler.hpp"
#include "GCSchedulerAdaptive.hpp"

namespace kotlin::gcScheduler {

class GCScheduler::Impl {
public:
    Impl(GCScheduler& owner, internal::HeapGrowthController& heapGrowthController, GCSchedulerConfig& config, gc::GC& gc) noexcept : impl_(owner, heapGrowthController, config, gc) {}

    GCSchedulerAdaptive<steady_clock>& impl() { return impl_; }

private:
    GCSchedulerAdaptive<steady_clock> impl_;
};

}
