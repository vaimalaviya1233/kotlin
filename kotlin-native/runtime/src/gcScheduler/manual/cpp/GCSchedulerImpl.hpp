/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "GCScheduler.hpp"
#include "GCThread.hpp"

namespace kotlin::gcScheduler {

class GCScheduler::Impl {
public:
    Impl(gc::GC& gc) noexcept : gcThread_(gc, *this) {
    }

    void onGCStarted(gc::GCHandle& handle) noexcept {}
    void onGCDidFinish(gc::GCHandle& handle) noexcept {}

    void schedule() noexcept  {
        ThreadStateGuard guard(ThreadState::kNative);
        gcThread_.schedule();
    }

    void scheduleAndWaitFullGC() noexcept  {
        ThreadStateGuard guard(ThreadState::kNative);
        auto scheduled_epoch = gcThread_.schedule();
        gcThread_.waitFinished(scheduled_epoch);
    }

    void scheduleAndWaitFullGCWithFinalizers() noexcept  {
        ThreadStateGuard guard(ThreadState::kNative);
        auto scheduled_epoch = gcThread_.schedule();
        gcThread_.waitFinalized(scheduled_epoch);
    }

private:
    internal::GCThread<Impl> gcThread_;
};

}
