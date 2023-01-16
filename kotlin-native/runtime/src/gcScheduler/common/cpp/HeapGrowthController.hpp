/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <algorithm>
#include <atomic>
#include <cmath>

#include "GCSchedulerConfig.hpp"
#include "KAssert.h"

namespace kotlin::gcScheduler::internal {

class HeapGrowthController {
public:
    explicit HeapGrowthController(GCSchedulerConfig config) noexcept : config_(config), allocationBytesLeft_(config_.targetHeapBytes) {}

    // Called by the mutators.
    // Returns true if needs GC.
    bool onAllocated(size_t allocatedBytes) noexcept {
        RuntimeAssert(allocatedBytes < static_cast<size_t>(std::numeric_limits<ssize_t>::max()) + 1, "allocatedBytes is too big %zu", allocatedBytes);
        auto remaining = allocationBytesLeft_.fetch_sub(static_cast<ssize_t>(allocatedBytes));
        return remaining < 0;
    }

    // Called by the GC thread.
    void onGCDone(size_t aliveSetBytes) noexcept {
        if (config_.autoTune) {
            double targetHeapBytes = static_cast<double>(aliveSetBytes) / config_.targetHeapUtilization;
            if (!std::isfinite(targetHeapBytes)) {
                // This shouldn't happen in practice: targetHeapUtilization is in (0, 1]. But in case it does, don't touch anything.
                return;
            }
            double minHeapBytes = static_cast<double>(config_.minHeapBytes);
            double maxHeapBytes = static_cast<double>(config_.maxHeapBytes);
            targetHeapBytes = std::min(std::max(targetHeapBytes, minHeapBytes), maxHeapBytes);
            config_.targetHeapBytes = static_cast<int64_t>(targetHeapBytes);
        }
        allocationBytesLeft_ += config_.targetHeapBytes;
    }

    // Called during the pause by the GC thread.
    void setConfig(GCSchedulerConfig config) noexcept {
        config_ = config;
    }

    const GCSchedulerConfig& config() const noexcept {
        return config_;
    }

private:
    GCSchedulerConfig config_;
    // Updated by both the mutators and the GC thread.
    std::atomic<ssize_t> allocationBytesLeft_ = 0;
};

}
