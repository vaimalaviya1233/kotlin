/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "StopTheWorldMarkAndSweep.hpp"

#include <cinttypes>

#include "CompilerConstants.hpp"
#include "GlobalData.hpp"
#include "GCStatistics.hpp"
#include "Logging.hpp"
#include "MarkAndSweepUtils.hpp"
#include "Memory.h"
#include "RootSet.hpp"
#include "Runtime.h"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"
#include "ThreadSuspension.hpp"

using namespace kotlin;

void gc::StopTheWorldMarkAndSweep::ThreadData::Schedule() noexcept {
    ThreadStateGuard guard(ThreadState::kNative);
    gc_.state_.schedule();
}

void gc::StopTheWorldMarkAndSweep::ThreadData::ScheduleAndWaitFullGC() noexcept {
    ThreadStateGuard guard(ThreadState::kNative);
    auto scheduled_epoch = gc_.state_.schedule();
    gc_.state_.waitEpochFinished(scheduled_epoch);
}

void gc::StopTheWorldMarkAndSweep::ThreadData::ScheduleAndWaitFullGCWithFinalizers() noexcept {
    ThreadStateGuard guard(ThreadState::kNative);
    auto scheduled_epoch = gc_.state_.schedule();
    gc_.state_.waitEpochFinalized(scheduled_epoch);
}

gc::StopTheWorldMarkAndSweep::StopTheWorldMarkAndSweep(
        gcScheduler::GCScheduler& gcScheduler,
        alloc::Allocator& allocator) noexcept :
    gcScheduler_(gcScheduler), allocator_(allocator) {
    allocator_.setFinalizerCompletion([this](int64_t epoch) noexcept {
        GCHandle::getByEpoch(epoch).finalizersDone();
        state_.finalized(epoch);
    });
    gcThread_ = ScopedThread(ScopedThread::attributes().name("GC thread"), [this] {
        while (true) {
            auto epoch = state_.waitScheduled();
            if (epoch.has_value()) {
                PerformFullGC(*epoch);
            } else {
                break;
            }
        }
    });
    RuntimeLogInfo({kTagGC}, "Stop-the-world Mark & Sweep GC initialized");
}

gc::StopTheWorldMarkAndSweep::~StopTheWorldMarkAndSweep() {
    state_.shutdown();
}

void gc::StopTheWorldMarkAndSweep::PerformFullGC(int64_t epoch) noexcept {
    auto gcHandle = GCHandle::create(epoch);
    bool didSuspend = mm::RequestThreadsSuspension();
    RuntimeAssert(didSuspend, "Only GC thread can request suspension");
    gcHandle.suspensionRequested();

    RuntimeAssert(!kotlin::mm::IsCurrentThreadRegistered(), "GC must run on unregistered thread");
    mm::WaitForThreadsSuspension();
    gcHandle.threadsAreSuspended();

    auto gcContext = allocator_.prepareForGC(gcHandle);

    auto& scheduler = gcScheduler_;
    scheduler.gcData().OnPerformFullGC();

    state_.start(epoch);

    gc::collectRootSet<internal::MarkTraits>(gcHandle, markQueue_, [](mm::ThreadData&) { return true; });

    gc::Mark<internal::MarkTraits>(gcHandle, markQueue_);
    auto markStats = gcHandle.getMarked();
    scheduler.gcData().UpdateAliveSetBytes(markStats.totalObjectsSize);
    gcContext.sweepExtraObjects();
    gcContext.sweep();

    mm::ResumeThreads();
    gcHandle.threadsAreResumed();
    state_.finish(epoch);
    gcHandle.finished();
}
