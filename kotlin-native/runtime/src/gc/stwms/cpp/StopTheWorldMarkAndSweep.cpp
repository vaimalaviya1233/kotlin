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

gc::StopTheWorldMarkAndSweep::StopTheWorldMarkAndSweep(
        mm::ObjectFactory<StopTheWorldMarkAndSweep>& objectFactory) noexcept :
    objectFactory_(objectFactory),
    finalizerProcessor_([](int64_t epoch) noexcept {
        GCHandle::getByEpoch(epoch).finalizersDone();
    }) {
    RuntimeLogDebug({kTagGC}, "Same thread Mark & Sweep GC initialized");
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

void gc::StopTheWorldMarkAndSweep::RunGC(GCHandle& gcHandle) noexcept {
    RuntimeLogDebug({kTagGC}, "Attempt to suspend threads by thread %d", konan::currentThreadId());
    bool didSuspend = mm::RequestThreadsSuspension();
    RuntimeAssert(didSuspend, "Only GC thread can request suspension");
    gcHandle.suspensionRequested();

    gcHandle.started();

    RuntimeAssert(!kotlin::mm::IsCurrentThreadRegistered(), "GC must run on unregistered thread");
    mm::WaitForThreadsSuspension();
    gcHandle.threadsAreSuspended();

    gc::collectRootSet<internal::MarkTraits>(gcHandle, markQueue_, [] (mm::ThreadData&) { return true; });
    auto& extraObjectsDataFactory = mm::GlobalData::Instance().extraObjectDataFactory();

    gc::Mark<internal::MarkTraits>(gcHandle, markQueue_);
    gc::SweepExtraObjects<SweepTraits>(gcHandle, extraObjectsDataFactory);
    auto finalizerQueue = gc::Sweep<SweepTraits>(gcHandle, objectFactory_);

    kotlin::compactObjectPoolInMainThread();

    mm::ResumeThreads();
    gcHandle.threadsAreResumed();
    gcHandle.finalizersScheduled(finalizerQueue.size());
    gcHandle.finished();
    finalizerProcessor_.ScheduleTasks(std::move(finalizerQueue), gcHandle.getEpoch());
}
