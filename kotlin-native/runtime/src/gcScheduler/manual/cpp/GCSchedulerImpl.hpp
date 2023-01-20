/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "GCScheduler.hpp"
#include "GCThread.hpp"
#include "Logging.hpp"

namespace kotlin::gcScheduler {

class GCScheduler::Impl {
public:
    Impl(gc::GC& gc) noexcept : gcThread_(gc, *this) {
    }

    void onGCStarted(gc::GCHandle& handle) noexcept {}
    void onGCDidFinish(gc::GCHandle& handle) noexcept {}

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

private:
    internal::GCThread<Impl> gcThread_;
};

}
