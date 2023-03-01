/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCScheduler.hpp"

#include <cmath>

#include "CompilerConstants.hpp"
#include "GCSchedulerImpl.hpp"
#include "GlobalData.hpp"
#include "KAssert.h"
#include "Porting.h"
#include "ThreadRegistry.hpp"
#include "ThreadData.hpp"

using namespace kotlin;

namespace {

std_support::unique_ptr<gc::GCSchedulerData> MakeGCSchedulerData(
        gc::SchedulerType type, gc::GCSchedulerConfig& config, std::function<void()> scheduleGC) noexcept {
    switch (type) {
        case gc::SchedulerType::kManual:
            return std_support::make_unique<gc::internal::GCSchedulerDataManual>();
        case gc::SchedulerType::kAdaptive:
            return std_support::make_unique<gc::internal::GCSchedulerDataAdaptive<steady_clock>>(config, std::move(scheduleGC));
        case gc::SchedulerType::kAggressive:
            return std_support::make_unique<gc::internal::GCSchedulerDataAggressive>(config, std::move(scheduleGC));
    }
}

} // namespace

void gc::GCScheduler::SetScheduleGC(std::function<void()> scheduleGC) noexcept {
    RuntimeAssert(static_cast<bool>(scheduleGC), "scheduleGC cannot be empty");
    RuntimeAssert(!static_cast<bool>(scheduleGC_), "scheduleGC must not have been set");
    scheduleGC_ = std::move(scheduleGC);
    RuntimeAssert(gcData_ == nullptr, "gcData_ must not be set prior to scheduleGC call");
    gcData_ = MakeGCSchedulerData(compiler::getGCSchedulerType(), config_, scheduleGC_);
}
