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

namespace {

struct SweepTraits {
    using ObjectFactory = mm::ObjectFactory<gc::StopTheWorldMarkAndSweep>;
    using ExtraObjectsFactory = mm::ExtraObjectDataFactory;

    static bool IsMarkedByExtraObject(mm::ExtraObjectData &object) noexcept {
        auto *baseObject = object.GetBaseObject();
        if (!baseObject->heap()) return true;
        auto& objectData = mm::ObjectFactory<gc::StopTheWorldMarkAndSweep>::NodeRef::From(baseObject).ObjectData();
        return objectData.marked();
    }

    static bool TryResetMark(ObjectFactory::NodeRef node) noexcept {
        auto& objectData = node.ObjectData();
        return objectData.tryResetMark();
    }
};

struct FinalizeTraits {
    using ObjectFactory = mm::ObjectFactory<gc::StopTheWorldMarkAndSweep>;
};

} // namespace

void gc::StopTheWorldMarkAndSweep::ThreadData::SafePointAllocation(size_t size) noexcept {
    gcScheduler_.OnSafePointAllocation(size);
    mm::SuspendIfRequested();
}

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

void gc::StopTheWorldMarkAndSweep::ThreadData::OnOOM(size_t size) noexcept {
    RuntimeLogDebug({kTagGC}, "Attempt to GC on OOM at size=%zu", size);
    ScheduleAndWaitFullGC();
}

gc::StopTheWorldMarkAndSweep::StopTheWorldMarkAndSweep(
        mm::ObjectFactory<StopTheWorldMarkAndSweep>& objectFactory, GCScheduler& gcScheduler) noexcept :
    objectFactory_(objectFactory),
    gcScheduler_(gcScheduler),
    finalizerProcessor_([this](int64_t epoch) noexcept {
        GCHandle::getByEpoch(epoch).finalizersDone();
        state_.finalized(epoch);
    }) {
    gcScheduler_.SetScheduleGC([this]() NO_INLINE {
        RuntimeLogDebug({kTagGC}, "Scheduling GC by thread %d", konan::currentThreadId());
        // This call acquires a lock, so we need to ensure that we're in the safe state.
        NativeOrUnregisteredThreadGuard guard(/* reentrant = */ true);
        state_.schedule();
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

void gc::StopTheWorldMarkAndSweep::StartFinalizerThreadIfNeeded() noexcept {
    NativeOrUnregisteredThreadGuard guard(true);
    finalizerProcessor_.StartFinalizerThreadIfNone();
    finalizerProcessor_.WaitFinalizerThreadInitialized();
}

void gc::StopTheWorldMarkAndSweep::StopFinalizerThreadIfRunning() noexcept {
    NativeOrUnregisteredThreadGuard guard(true);
    finalizerProcessor_.StopFinalizerThread();
}

bool gc::StopTheWorldMarkAndSweep::FinalizersThreadIsRunning() noexcept {
    return finalizerProcessor_.IsRunning();
}

void gc::StopTheWorldMarkAndSweep::PerformFullGC(int64_t epoch) noexcept {
    auto gcHandle = GCHandle::create(epoch);
    bool didSuspend = mm::RequestThreadsSuspension();
    RuntimeAssert(didSuspend, "Only GC thread can request suspension");
    gcHandle.suspensionRequested();

    RuntimeAssert(!kotlin::mm::IsCurrentThreadRegistered(), "GC must run on unregistered thread");
    mm::WaitForThreadsSuspension();
    gcHandle.threadsAreSuspended();

    auto& scheduler = gcScheduler_;
    scheduler.gcData().OnPerformFullGC();

    state_.start(epoch);

    gc::collectRootSet<internal::MarkTraits>(gcHandle, markQueue_, [] (mm::ThreadData&) { return true; });
    auto& extraObjectsDataFactory = mm::GlobalData::Instance().extraObjectDataFactory();

    gc::Mark<internal::MarkTraits>(gcHandle, markQueue_);
    auto markStats = gcHandle.getMarked();
    scheduler.gcData().UpdateAliveSetBytes(markStats.totalObjectsSize);
    gc::SweepExtraObjects<SweepTraits>(gcHandle, extraObjectsDataFactory);
    auto finalizerQueue = gc::Sweep<SweepTraits>(gcHandle, objectFactory_);

    kotlin::compactObjectPoolInMainThread();

    mm::ResumeThreads();
    gcHandle.threadsAreResumed();
    state_.finish(epoch);
    gcHandle.finalizersScheduled(finalizerQueue.size());
    gcHandle.finished();
    finalizerProcessor_.ScheduleTasks(std::move(finalizerQueue), epoch);
}
