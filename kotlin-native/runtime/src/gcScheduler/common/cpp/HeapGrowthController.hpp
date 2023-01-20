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
    enum class AllocationBoundary {
        kNone,
        kWeak,
        kStrong,
    };

    explicit HeapGrowthController(GCSchedulerConfig config) noexcept : config_(config), allocationBytesLeft_(config_.weakTargetHeapBytes()), strongAllocationBoundaryBytes_(config.weakTargetHeapBytes() - config.targetHeapBytes) {}

    // Called by the mutators.
    int64_t onAllocated(uint64_t allocatedBytes) noexcept {
        // Expecting that allocatedBytes is way less than max uint64_t.
        // True for 32 bit and current 64 bit platforms.
        return allocationBytesLeft_.fetch_sub(allocatedBytes);
    }

    AllocationBoundary computeBoundary(int64_t remaining) const noexcept {
        if (remaining >= 0) {
            return AllocationBoundary::kNone;
        }

        if (remaining >= strongAllocationBoundaryBytes_) {
            return AllocationBoundary::kWeak;
        }

        return AllocationBoundary::kStrong;
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
        auto weakTargetHeapBytes = config_.weakTargetHeapBytes();
        // TODO: Is this desynchronization bad?
        strongAllocationBoundaryBytes_ = weakTargetHeapBytes - config_.targetHeapBytes;
        allocationBytesLeft_ += weakTargetHeapBytes;
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
    std::atomic<int64_t> allocationBytesLeft_ = 0;
    std::atomic<int64_t> strongAllocationBoundaryBytes_;
};

}
