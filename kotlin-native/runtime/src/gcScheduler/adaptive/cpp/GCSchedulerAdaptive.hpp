/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "AppStateTracking.hpp"
#include "GC.hpp"
#include "GCSchedulerConfig.hpp"
#include "GCStatistics.hpp"
#include "GlobalData.hpp"
#include "GCThread.hpp"
#include "HeapGrowthController.hpp"
#include "Logging.hpp"
#include "Memory.h"
#include "RegularIntervalPacer.hpp"
#include "RepeatedTimer.hpp"
#include "Porting.h"

namespace kotlin::gcScheduler {

template <typename Clock>
class GCSchedulerAdaptive {
public:
    GCSchedulerAdaptive(GCScheduler& owner, GCSchedulerConfig initialConfig, gc::GC& gc) noexcept :
        owner_(owner),
        appStateTracking_(mm::GlobalData::Instance().appStateTracking()),
        heapGrowthController_(initialConfig),
        regularIntervalPacer_(initialConfig),
        gcThread_(gc, *this),
        timer_("GC Timer thread", initialConfig.regularGcInterval(), &GCSchedulerAdaptive::timerRoutine, this) {
        }

    void onAllocation(size_t allocatedBytes) noexcept  {
        auto remaining = heapGrowthController_.onAllocated(allocatedBytes);
        if (remaining >= 0)
            return;
        onAllocationSlowPath(remaining);
    }

    void schedule() noexcept  {
        ThreadStateGuard guard(ThreadState::kNative);
        RuntimeLogDebug({kTagGC}, "Scheduling forced GC by thread %d", konan::currentThreadId());
        gcThread_.state().schedule();
    }

    void scheduleAndWaitFullGC() noexcept  {
        ThreadStateGuard guard(ThreadState::kNative);
        RuntimeLogDebug({kTagGC}, "Scheduling forced GC by thread %d and waiting for its completion", konan::currentThreadId());
        auto& state = gcThread_.state();
        auto scheduled_epoch = state.schedule();
        state.waitEpochFinished(scheduled_epoch);
    }

    void scheduleAndWaitFullGCWithFinalizers() noexcept  {
        ThreadStateGuard guard(ThreadState::kNative);
        RuntimeLogDebug({kTagGC}, "Scheduling forced GC by thread %d and waiting for its completion together with finalizers", konan::currentThreadId());
        auto& state = gcThread_.state();
        auto scheduled_epoch = state.schedule();
        state.waitEpochFinalized(scheduled_epoch);
    }

    void onOOM(uint64_t size) noexcept  {
        ThreadStateGuard guard(ThreadState::kNative);
        RuntimeLogDebug({kTagGC}, "Forcing GC by OOM of size=%" PRIu64 " by thread %d", size, konan::currentThreadId());
        auto& state = gcThread_.state();
        auto scheduled_epoch = state.schedule();
        state.waitEpochFinished(scheduled_epoch);
    }

    // Called on the GC thread during the pause.
    void onGCStarted(gc::GCHandle& handle) noexcept {
        auto config = owner_.readConfig([](const auto& config) noexcept { return config; });
        heapGrowthController_.setConfig(config);
        std::unique_lock guard(regularIntervalPacerMutex_);
        regularIntervalPacer_.setConfig(config);
    }

    // Called on the GC thread.
    void onGCDidFinish(gc::GCHandle& handle) noexcept {
        heapGrowthController_.onGCDone(handle.getMarked().totalObjectsSize);
        regularIntervalPacer_.onGCDone();
        owner_.modifyConfig([this](auto& config) noexcept {
            // If the user modified stuff during the GC run, it'll be applied on the next GC.
            config.mergeAutotunedConfig(
                heapGrowthController_.config()
            );
            timer_.restart(config.regularGcInterval());
        });
    }

private:
    void timerRoutine() noexcept {
        if (appStateTracking_.state() == mm::AppStateTracking::State::kBackground) {
            return;
        }
        bool needsGC;
        {
            std::unique_lock guard(regularIntervalPacerMutex_);
            needsGC = regularIntervalPacer_.needsGC();
        }
        if (needsGC) {
            RuntimeLogDebug({kTagGC}, "Scheduling GC by timer");
            gcThread_.state().schedule();
        }
    }

    void onAllocationSlowPath(int64_t remaining) noexcept {
        switch (heapGrowthController_.computeBoundary(remaining)) {
            case internal::HeapGrowthController::AllocationBoundary::kNone:
                RuntimeAssert(false, "Handled by the caller");
                return;
            case internal::HeapGrowthController::AllocationBoundary::kWeak:
                onAllocationWeakBoundary();
                return;
            case internal::HeapGrowthController::AllocationBoundary::kStrong:
                onAllocationStrongBoundary();
                return;
        }
    }

    void onAllocationWeakBoundary() noexcept {
        ThreadStateGuard guard(ThreadState::kNative);
        RuntimeLogDebug({kTagGC}, "Scheduling GC by allocation threshold by thread %d", konan::currentThreadId());
        gcThread_.state().ensureActive();
    }

    void onAllocationStrongBoundary() noexcept {
        ThreadStateGuard guard(ThreadState::kNative);
        RuntimeLogDebug({kTagGC}, "Scheduling GC by severe allocation threshold by thread %d", konan::currentThreadId());
        auto& state = gcThread_.state();
        auto scheduled_epoch = state.ensureActive();
        state.waitEpochFinished(scheduled_epoch);
    }

    GCScheduler& owner_;
    mm::AppStateTracking& appStateTracking_;
    internal::HeapGrowthController heapGrowthController_;
    Mutex<MutexThreadStateHandling::kSwitchIfRegistered> regularIntervalPacerMutex_;
    internal::RegularIntervalPacer<Clock> regularIntervalPacer_;
    internal::GCThread<GCSchedulerAdaptive> gcThread_;
    RepeatedTimer<Clock> timer_;
};

}
