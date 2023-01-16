/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "GCScheduler.hpp"
#include "GCStatistics.hpp"
#include "GCThread.hpp"
#include "HeapGrowthController.hpp"
#include "SafePointTracker.hpp"

namespace kotlin::gcScheduler {

class GCScheduler::Impl {
public:
    Impl(GCScheduler& owner, GCSchedulerConfig initialConfig, gc::GC& gc) noexcept :
        owner_(owner), heapGrowthController_(initialConfig), gcThread_(gc, *this)  {
    }

    // Called by mutator threads.
    void onAllocation(size_t allocatedBytes) noexcept {
        bool needsGC = false;
        if (heapGrowthController_.onAllocated(allocatedBytes)) {
            needsGC = true;
        } else if (safePointTracker_.registerCurrentSafePoint(1)) {
            needsGC = true;
        }
        if (needsGC) {
        // Can't wait for finalizers as they may recursively call GC.
            scheduleAndWaitFullGC();
        }
    }

    void schedule() noexcept  {
        ThreadStateGuard guard(ThreadState::kNative);
        gcThread_.schedule();
    }

    void scheduleAndWaitFullGC() noexcept  {
        ThreadStateGuard guard(ThreadState::kNative);
        auto scheduled_epoch = gcThread_.schedule();
        gcThread_.waitFinished(scheduled_epoch);
    }

    void scheduleAndWaitFullGCWithFinalizers() noexcept {
        ThreadStateGuard guard(ThreadState::kNative);
        auto scheduled_epoch = gcThread_.schedule();
        gcThread_.waitFinalized(scheduled_epoch);
    }

    void onOOM(size_t size) noexcept {
        RuntimeLogDebug({kTagGC}, "Attempt to GC on OOM at size=%zu", size);
        scheduleAndWaitFullGC();
    }

    // Called by mutator threads.
    void onSafePoint() noexcept  {
        if (safePointTracker_.registerCurrentSafePoint(1)) {
            // Can't wait for finalizers as they may recursively call GC.
            scheduleAndWaitFullGC();
        }
        // TODO: Consider waiting for the running GC to complete.
    }

    // Called on the GC thread during the pause.
    void onGCStarted(gc::GCHandle& handle) noexcept {
        auto config = owner_.readConfig([](const auto& config) noexcept { return config; });
        heapGrowthController_.setConfig(config);
    }

    // Called on the GC thread.
    void onGCDidFinish(gc::GCHandle& handle) noexcept {
        heapGrowthController_.onGCDone(handle.getMarked().totalObjectsSize);
        owner_.modifyConfig([this](auto& config) noexcept {
            // If the user modified stuff during the GC run, it'll be applied on the next GC.
            config.mergeAutotunedConfig(
                heapGrowthController_.config()
            );
        });
    }

private:
    GCScheduler& owner_;
    internal::HeapGrowthController heapGrowthController_;
    internal::SafePointTracker<> safePointTracker_;
    internal::GCThread<Impl> gcThread_;
};

}
