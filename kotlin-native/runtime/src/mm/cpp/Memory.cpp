/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "Memory.h"
#include "MemoryPrivate.hpp"

#include "Exceptions.h"
#include "ExtraObjectData.hpp"
#include "Freezing.hpp"
#include "GC.hpp"
#include "GlobalsRegistry.hpp"
#include "KAssert.h"
#include "Natives.h"
#include "ObjectOps.hpp"
#include "Porting.h"
#include "Runtime.h"
#include "StableRefRegistry.hpp"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"
#include "ThreadState.hpp"
#include "Utils.hpp"

using namespace kotlin;

// TODO: This name does not make sense anymore.
// Delete all means of creating this type directly as it only serves
// as a typedef for `mm::StableRefRegistry::Node`.
class ForeignRefManager : Pinned {
public:
    ForeignRefManager() = delete;
    ~ForeignRefManager() = delete;
};

namespace {

// `reinterpret_cast` to it and back to the same type
// will yield precisely the same pointer, so it's safe.
ALWAYS_INLINE ForeignRefManager* ToForeignRefManager(mm::StableRefRegistry::Node* data) {
    return reinterpret_cast<ForeignRefManager*>(data);
}

ALWAYS_INLINE mm::StableRefRegistry::Node* FromForeignRefManager(ForeignRefManager* manager) {
    return reinterpret_cast<mm::StableRefRegistry::Node*>(manager);
}

} // namespace

ObjHeader* ObjHeader::GetWeakCounter() {
    return mm::ExtraObjectData::FromMetaObjHeader(this->meta_object()).GetWeakReferenceCounter();
}

ObjHeader* ObjHeader::GetOrSetWeakCounter(ObjHeader* counter) {
    return mm::ExtraObjectData::FromMetaObjHeader(this->meta_object()).GetOrSetWeakReferenceCounter(this, counter);
}

#ifdef KONAN_OBJC_INTEROP

void* ObjHeader::GetAssociatedObject() const {
    auto metaObject = meta_object_or_null();
    if (metaObject == nullptr) {
        return nullptr;
    }
    return mm::ExtraObjectData::FromMetaObjHeader(metaObject).AssociatedObject().load(std::memory_order_acquire);
}

void ObjHeader::SetAssociatedObject(void* obj) {
    return mm::ExtraObjectData::FromMetaObjHeader(meta_object()).AssociatedObject().store(obj, std::memory_order_release);
}

void* ObjHeader::CasAssociatedObject(void* expectedObj, void* obj) {
    mm::ExtraObjectData::FromMetaObjHeader(meta_object()).AssociatedObject().compare_exchange_strong(expectedObj, obj);
    return expectedObj;
}

#endif // KONAN_OBJC_INTEROP

// static
MetaObjHeader* ObjHeader::createMetaObject(ObjHeader* object) {
    return mm::ExtraObjectData::Install(object).AsMetaObjHeader();
}

// static
void ObjHeader::destroyMetaObject(ObjHeader* object) {
    RuntimeAssert(object->has_meta_object(), "Object must have a meta object set");
    auto &extraObject = *mm::ExtraObjectData::Get(object);
    extraObject.Uninstall();
    auto *threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    mm::ExtraObjectDataFactory::Instance().DestroyExtraObjectData(threadData, extraObject);
}

ALWAYS_INLINE bool isPermanentOrFrozen(const ObjHeader* obj) {
    // TODO: Freeze TF_IMMUTABLE objects upon creation.
    if (!compiler::freezingChecksEnabled()) return false;
    return mm::IsFrozen(obj) || ((obj->type_info()->flags_ & TF_IMMUTABLE) != 0);
}

ALWAYS_INLINE bool isShareable(const ObjHeader* obj) {
    // TODO: Remove when legacy MM is gone.
    return true;
}

extern "C" MemoryState* InitMemory(bool firstRuntime) {
    return mm::ToMemoryState(mm::ThreadRegistry::Instance().RegisterCurrentThread());
}

extern "C" void DeinitMemory(MemoryState* state, bool destroyRuntime) {
    // We need the native state to avoid a deadlock on unregistering the thread.
    // The deadlock is possible if we are in the runnable state and the GC already locked
    // the thread registery and waits for threads to suspend or go to the native state.
    AssertThreadState(state, ThreadState::kNative);
    auto* node = mm::FromMemoryState(state);
    if (destroyRuntime) {
        ThreadStateGuard guard(state, ThreadState::kRunnable);
        mm::GlobalData::Instance().gcScheduler().scheduleAndWaitFullGCWithFinalizers();
        // TODO: Why not just destruct `GC` object and its thread data counterpart entirely?
        mm::GlobalData::Instance().gc().StopFinalizerThreadIfRunning();
    }
    if (!konan::isOnThreadExitNotSetOrAlreadyStarted()) {
        // we can clear reference in advance, as Unregister function can't use it anyway
        mm::ThreadRegistry::ClearCurrentThreadData();
    }
    mm::ThreadRegistry::Instance().Unregister(node);
}

extern "C" void RestoreMemory(MemoryState*) {
    // TODO: Remove when legacy MM is gone.
}

extern "C" void ClearMemoryForTests(MemoryState* state) {
    state->GetThreadData()->ClearForTests();
}

extern "C" RUNTIME_NOTHROW OBJ_GETTER(AllocInstance, const TypeInfo* typeInfo) {
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    RETURN_RESULT_OF(mm::AllocateObject, threadData, typeInfo);
}

extern "C" OBJ_GETTER(AllocArrayInstance, const TypeInfo* typeInfo, int32_t elements) {
    if (elements < 0) {
        ThrowIllegalArgumentException();
    }
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    RETURN_RESULT_OF(mm::AllocateArray, threadData, typeInfo, static_cast<uint32_t>(elements));
}

extern "C" RUNTIME_NOTHROW void InitAndRegisterGlobal(ObjHeader** location, const ObjHeader* initialValue) {
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    mm::GlobalsRegistry::Instance().RegisterStorageForGlobal(threadData, location);
    // Null `initialValue` means that the appropriate value was already set by static initialization.
    if (initialValue != nullptr) {
        mm::SetHeapRef(location, const_cast<ObjHeader*>(initialValue));
    }
}

extern "C" const MemoryModel CurrentMemoryModel = MemoryModel::kExperimental;

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void SetStackRef(ObjHeader** location, const ObjHeader* object) {
    mm::SetStackRef(location, const_cast<ObjHeader*>(object));
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void SetHeapRef(ObjHeader** location, const ObjHeader* object) {
    mm::SetHeapRef(location, const_cast<ObjHeader*>(object));
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void ZeroHeapRef(ObjHeader** location) {
    mm::SetHeapRef(location, nullptr);
}

extern "C" RUNTIME_NOTHROW void ZeroArrayRefs(ArrayHeader* array) {
    for (uint32_t index = 0; index < array->count_; ++index) {
        ObjHeader** location = ArrayAddressOfElementAt(array, index);
        mm::SetHeapRef(location, nullptr);
    }
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void ZeroStackRef(ObjHeader** location) {
    mm::SetStackRef(location, nullptr);
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void UpdateStackRef(ObjHeader** location, const ObjHeader* object) {
    mm::SetStackRef(location, const_cast<ObjHeader*>(object));
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void UpdateHeapRef(ObjHeader** location, const ObjHeader* object) {
    mm::SetHeapRef(location, const_cast<ObjHeader*>(object));
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void UpdateVolatileHeapRef(ObjHeader** location, const ObjHeader* object) {
    mm::SetHeapRefAtomicSeqCst(location, const_cast<ObjHeader*>(object));
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW OBJ_GETTER(CompareAndSwapVolatileHeapRef, ObjHeader** location, ObjHeader* expectedValue, ObjHeader* newValue) {
    RETURN_RESULT_OF(mm::CompareAndSwapHeapRef, location, expectedValue, newValue);
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW bool CompareAndSetVolatileHeapRef(ObjHeader** location, ObjHeader* expectedValue, ObjHeader* newValue) {
    return mm::CompareAndSetHeapRef(location, expectedValue, newValue);
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW OBJ_GETTER(GetAndSetVolatileHeapRef, ObjHeader** location, ObjHeader* newValue) {
    RETURN_RESULT_OF(mm::GetAndSetHeapRef, location, newValue);
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void UpdateHeapRefIfNull(ObjHeader** location, const ObjHeader* object) {
    if (object == nullptr) return;
    ObjHeader* result = nullptr; // No need to store this value in a rootset.
    mm::CompareAndSwapHeapRef(location, nullptr, const_cast<ObjHeader*>(object), &result);
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void UpdateHeapRefsInsideOneArray(const ArrayHeader* array, int fromIndex,
                                                                           int toIndex, int count) {
    RuntimeFail("Only for legacy MM");
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void UpdateReturnRef(ObjHeader** returnSlot, const ObjHeader* object) {
    mm::SetStackRef(returnSlot, const_cast<ObjHeader*>(object));
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW OBJ_GETTER(
        SwapHeapRefLocked, ObjHeader** location, ObjHeader* expectedValue, ObjHeader* newValue, int32_t* spinlock, int32_t* cookie) {
    RETURN_RESULT_OF(mm::CompareAndSwapHeapRef, location, expectedValue, newValue);
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW void SetHeapRefLocked(
        ObjHeader** location, ObjHeader* newValue, int32_t* spinlock, int32_t* cookie) {
    mm::SetHeapRefAtomic(location, newValue);
}

extern "C" ALWAYS_INLINE RUNTIME_NOTHROW OBJ_GETTER(ReadHeapRefLocked, ObjHeader** location, int32_t* spinlock, int32_t* cookie) {
    RETURN_RESULT_OF(mm::ReadHeapRefAtomic, location);
}

extern "C" OBJ_GETTER(ReadHeapRefNoLock, ObjHeader* object, int32_t index) {
    // TODO: Remove when legacy MM is gone.
    ThrowNotImplementedError();
}

extern "C" RUNTIME_NOTHROW void EnterFrame(ObjHeader** start, int parameters, int count) {
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    threadData->shadowStack().EnterFrame(start, parameters, count);
}

extern "C" RUNTIME_NOTHROW void LeaveFrame(ObjHeader** start, int parameters, int count) {
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    threadData->shadowStack().LeaveFrame(start, parameters, count);
}

extern "C" RUNTIME_NOTHROW void SetCurrentFrame(ObjHeader** start) {
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    threadData->shadowStack().SetCurrentFrame(start);
}

extern "C" RUNTIME_NOTHROW FrameOverlay* getCurrentFrame() {
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    return threadData->shadowStack().getCurrentFrame();
}

extern "C" RUNTIME_NOTHROW ALWAYS_INLINE void CheckCurrentFrame(ObjHeader** frame) {
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    return threadData->shadowStack().checkCurrentFrame(reinterpret_cast<FrameOverlay*>(frame));
}

extern "C" RUNTIME_NOTHROW void AddTLSRecord(MemoryState* memory, void** key, int size) {
    auto* threadData = memory->GetThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    threadData->tls().AddRecord(key, size);
}

extern "C" RUNTIME_NOTHROW void CommitTLSStorage(MemoryState* memory) {
    auto* threadData = memory->GetThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    threadData->tls().Commit();
}

extern "C" RUNTIME_NOTHROW void ClearTLS(MemoryState* memory) {
    auto* threadData = memory->GetThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    threadData->tls().Clear();
}

extern "C" RUNTIME_NOTHROW ObjHeader** LookupTLS(void** key, int index) {
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    return threadData->tls().Lookup(key, index);
}

extern "C" RUNTIME_NOTHROW void GC_RegisterWorker(void* worker) {
    // TODO: Remove when legacy MM is gone.
    // Nothing to do
}

extern "C" RUNTIME_NOTHROW void GC_UnregisterWorker(void* worker) {
    // TODO: Remove when legacy MM is gone.
    // Nothing to do
}

extern "C" RUNTIME_NOTHROW void GC_CollectorCallback(void* worker) {
    // TODO: Remove when legacy MM is gone.
    // Nothing to do
}

extern "C" void Kotlin_native_internal_GC_collect(ObjHeader*) {
    AssertThreadState(ThreadState::kRunnable);
    mm::GlobalData::Instance().gcScheduler().scheduleAndWaitFullGCWithFinalizers();
}

extern "C" void Kotlin_native_internal_GC_schedule(ObjHeader*) {
    AssertThreadState(ThreadState::kRunnable);
    mm::GlobalData::Instance().gcScheduler().schedule();
}

extern "C" void Kotlin_native_internal_GC_collectCyclic(ObjHeader*) {
    // TODO: Remove when legacy MM is gone.
    // Nothing to do
}

// TODO: Maybe a pair of suspend/resume or start/stop may be useful in the future?
//       The other pair is likely to be removed.

extern "C" void Kotlin_native_internal_GC_suspend(ObjHeader*) {
    // Nothing to do
}

extern "C" void Kotlin_native_internal_GC_resume(ObjHeader*) {
    // Nothing to do
}

extern "C" void Kotlin_native_internal_GC_stop(ObjHeader*) {
    // Nothing to do
}

extern "C" void Kotlin_native_internal_GC_start(ObjHeader*) {
    // Nothing to do
}

#define SET_GC_SCHEDULER_CONFIG(exportType, exportName, internalName) \
    extern "C" void Kotlin_native_internal_GC_set ## exportName(ObjHeader*, exportType value) { \
        mm::GlobalData::Instance().gcScheduler().modifyConfig([=](gcScheduler::GCSchedulerConfig& config) noexcept { config.internalName = value; }); \
    }

#define SET_GC_SCHEDULER_CONFIG_LEGACY(exportType, exportName) \
    extern "C" void Kotlin_native_internal_GC_set ## exportName(ObjHeader*, exportType value) {}

#define GET_GC_SCHEDULER_CONFIG(exportType, exportName, internalName) \
    extern "C" exportType Kotlin_native_internal_GC_get ## exportName(ObjHeader*) { \
        return mm::GlobalData::Instance().gcScheduler().readConfig([](const gcScheduler::GCSchedulerConfig& config) noexcept { return config.internalName; }); \
    }

#define GET_GC_SCHEDULER_CONFIG_LEGACY(exportType, exportName) \
    extern "C" exportType Kotlin_native_internal_GC_get ## exportName(ObjHeader*) { return {}; }

#define GC_SCHEDULER_CONFIG(exportType, exportName, internalName) \
    GET_GC_SCHEDULER_CONFIG(exportType, exportName, internalName) \
    SET_GC_SCHEDULER_CONFIG(exportType, exportName, internalName) \

// TODO: Remove when legacy MM is gone.
#define GC_SCHEDULER_CONFIG_LEGACY(exportType, exportName) \
    GET_GC_SCHEDULER_CONFIG_LEGACY(exportType, exportName) \
    SET_GC_SCHEDULER_CONFIG_LEGACY(exportType, exportName) \

GC_SCHEDULER_CONFIG_LEGACY(KInt, Threshold)
GC_SCHEDULER_CONFIG_LEGACY(int64_t, CollectCyclesThreshold)
GC_SCHEDULER_CONFIG_LEGACY(int64_t, ThresholdAllocations)

GC_SCHEDULER_CONFIG(KBoolean, TuneThreshold, autoTune)
GC_SCHEDULER_CONFIG(KLong, RegularGCIntervalMicroseconds, regularGcIntervalMicroseconds)
GC_SCHEDULER_CONFIG(KLong, TargetHeapBytes, targetHeapBytes)
GC_SCHEDULER_CONFIG(KLong, MaxHeapBytes, maxHeapBytes)
GC_SCHEDULER_CONFIG(KLong, MinHeapBytes, minHeapBytes)
GC_SCHEDULER_CONFIG(KDouble, TargetHeapUtilization, targetHeapUtilization)
GC_SCHEDULER_CONFIG(KDouble, WeakTargetHeapCoefficient, weakTargetHeapCoefficient)

extern "C" OBJ_GETTER(Kotlin_native_internal_GC_detectCycles, ObjHeader*) {
    // TODO: Remove when legacy MM is gone.
    RETURN_OBJ(nullptr);
}

extern "C" OBJ_GETTER(Kotlin_native_internal_GC_findCycle, ObjHeader*, ObjHeader* root) {
    // TODO: Remove when legacy MM is gone.
    RETURN_OBJ(nullptr);
}

extern "C" bool Kotlin_native_internal_GC_getCyclicCollector(ObjHeader* gc) {
    // TODO: Remove when legacy MM is gone.
    // Nothing to do.
    return false;
}

extern "C" void Kotlin_native_internal_GC_setCyclicCollector(ObjHeader* gc, bool value) {
    // TODO: Remove when legacy MM is gone.
    // Nothing to do.
}

extern "C" bool Kotlin_Any_isShareable(ObjHeader* thiz) {
    // TODO: Remove when legacy MM is gone.
    return true;
}

extern "C" void Kotlin_Any_share(ObjHeader* thiz) {
    // TODO: Remove when legacy MM is gone.
    // Nothing to do
}

extern "C" RUNTIME_NOTHROW void PerformFullGC(MemoryState* memory) {
    AssertThreadState(memory, ThreadState::kRunnable);
    mm::GlobalData::Instance().gcScheduler().scheduleAndWaitFullGCWithFinalizers();
}

extern "C" bool TryAddHeapRef(const ObjHeader* object) {
    RuntimeFail("Only for legacy MM");
}

extern "C" RUNTIME_NOTHROW void ReleaseHeapRefNoCollect(const ObjHeader* object) {
    RuntimeFail("Only for legacy MM");
}

extern "C" RUNTIME_NOTHROW OBJ_GETTER(TryRef, ObjHeader* object) {
    // TODO: With CMS this needs:
    //       * during marking phase if `object` is unmarked: barrier (might be automatic because of the stack write)
    //         and return `object`;
    //       * during marking phase if `object` is marked: return `object`;
    //       * during sweeping phase if `object` is unmarked: return nullptr;
    //       * during sweeping phase if `object` is marked: return `object`;
    RETURN_OBJ(object);
}

extern "C" RUNTIME_NOTHROW bool ClearSubgraphReferences(ObjHeader* root, bool checked) {
    // TODO: Remove when legacy MM is gone.
    return true;
}

extern "C" RUNTIME_NOTHROW void* CreateStablePointer(ObjHeader* object) {
    if (!object)
        return nullptr;

    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    return mm::StableRefRegistry::Instance().RegisterStableRef(threadData, object);
}

extern "C" RUNTIME_NOTHROW void DisposeStablePointer(void* pointer) {
    DisposeStablePointerFor(kotlin::mm::GetMemoryState(), pointer);
}

extern "C" RUNTIME_NOTHROW void DisposeStablePointerFor(MemoryState* memoryState, void* pointer) {
    if (!pointer)
        return;

    auto* threadData = memoryState->GetThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);

    auto* node = static_cast<mm::StableRefRegistry::Node*>(pointer);
    mm::StableRefRegistry::Instance().UnregisterStableRef(threadData, node);
}

extern "C" RUNTIME_NOTHROW OBJ_GETTER(DerefStablePointer, void* pointer) {
    if (!pointer)
        RETURN_OBJ(nullptr);

    AssertThreadState(ThreadState::kRunnable);

    auto* node = static_cast<mm::StableRefRegistry::Node*>(pointer);
    ObjHeader* object = **node;
    RETURN_OBJ(object);
}

extern "C" RUNTIME_NOTHROW OBJ_GETTER(AdoptStablePointer, void* pointer) {
    if (!pointer)
        RETURN_OBJ(nullptr);

    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    AssertThreadState(threadData, ThreadState::kRunnable);
    auto* node = static_cast<mm::StableRefRegistry::Node*>(pointer);
    ObjHeader* object = **node;
    // Make sure `object` stays in the rootset: put it on the stack before removing it from `StableRefRegistry`.
    mm::SetStackRef(OBJ_RESULT, object);
    mm::StableRefRegistry::Instance().UnregisterStableRef(threadData, node);
    return object;
}

extern "C" void MutationCheck(ObjHeader* obj) {
    if (obj->local()) return;
    if (!isPermanentOrFrozen(obj)) return;

    ThrowInvalidMutabilityException(obj);
}

extern "C" RUNTIME_NOTHROW void CheckLifetimesConstraint(ObjHeader* obj, ObjHeader* pointee) {
    RuntimeAssert(obj->local() || pointee == nullptr || !pointee->local(),
                  "Attempt to store a stack object %p into a heap object %p. "
                  "This is a compiler bug, please report it to https://kotl.in/issue",
                  pointee, obj);
}

extern "C" void FreezeSubgraph(ObjHeader* obj) {
    if (auto* blocker = mm::FreezeSubgraph(obj)) {
        ThrowFreezingException(obj, blocker);
    }
}

extern "C" void EnsureNeverFrozen(ObjHeader* obj) {
    if (!mm::EnsureNeverFrozen(obj)) {
        ThrowFreezingException(obj, obj);
    }
}

extern "C" ForeignRefContext InitLocalForeignRef(ObjHeader* object) {
    AssertThreadState(ThreadState::kRunnable);
    // TODO: Remove when legacy MM is gone.
    // Nothing to do.
    return nullptr;
}

extern "C" ForeignRefContext InitForeignRef(ObjHeader* object) {
    AssertThreadState(ThreadState::kRunnable);
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    auto* node = mm::StableRefRegistry::Instance().RegisterStableRef(threadData, object);
    return ToForeignRefManager(node);
}

extern "C" void DeinitForeignRef(ObjHeader* object, ForeignRefContext context) {
    AssertThreadState(ThreadState::kRunnable);
    RuntimeAssert(context != nullptr, "DeinitForeignRef must not be called for InitLocalForeignRef");
    auto* threadData = mm::ThreadRegistry::Instance().CurrentThreadData();
    auto* node = FromForeignRefManager(context);
    RuntimeAssert(object == **node, "Must correspond to the same object");
    mm::StableRefRegistry::Instance().UnregisterStableRef(threadData, node);
}

extern "C" bool IsForeignRefAccessible(ObjHeader* object, ForeignRefContext context) {
    // TODO: Remove when legacy MM is gone.
    return true;
}

extern "C" void AdoptReferenceFromSharedVariable(ObjHeader* object) {
    // TODO: Remove when legacy MM is gone.
    // Nothing to do.
}

extern "C" void CheckGlobalsAccessible() {
    // TODO: Remove when legacy MM is gone.
    // Always accessible
}

// it would be inlined manually in RemoveRedundantSafepointsPass
extern "C" RUNTIME_NOTHROW NO_INLINE void Kotlin_mm_safePointFunctionPrologue() {
    AssertThreadState(ThreadState::kRunnable);
    auto& globalData = mm::GlobalData::Instance();
    globalData.gcScheduler().onSafePoint();
    globalData.gc().OnSafePoint();
}

extern "C" RUNTIME_NOTHROW CODEGEN_INLINE_POLICY void Kotlin_mm_safePointWhileLoopBody() {
    AssertThreadState(ThreadState::kRunnable);
    auto& globalData = mm::GlobalData::Instance();
    globalData.gcScheduler().onSafePoint();
    globalData.gc().OnSafePoint();
}

extern "C" CODEGEN_INLINE_POLICY RUNTIME_NOTHROW void Kotlin_mm_switchThreadStateNative() {
    SwitchThreadState(mm::ThreadRegistry::Instance().CurrentThreadData(), ThreadState::kNative);
}

extern "C" CODEGEN_INLINE_POLICY RUNTIME_NOTHROW void Kotlin_mm_switchThreadStateRunnable() {
    SwitchThreadState(mm::ThreadRegistry::Instance().CurrentThreadData(), ThreadState::kRunnable);
}

MemoryState* kotlin::mm::GetMemoryState() noexcept {
    return ToMemoryState(ThreadRegistry::Instance().CurrentThreadDataNode());
}

bool kotlin::mm::IsCurrentThreadRegistered() noexcept {
    return ThreadRegistry::Instance().IsCurrentThreadRegistered();
}

ALWAYS_INLINE kotlin::CalledFromNativeGuard::CalledFromNativeGuard(bool reentrant) noexcept : reentrant_(reentrant) {
    Kotlin_initRuntimeIfNeeded();
    thread_ = mm::GetMemoryState();
    oldState_ = SwitchThreadState(thread_, ThreadState::kRunnable, reentrant_);
}

const bool kotlin::kSupportsMultipleMutators = kotlin::gc::kSupportsMultipleMutators;

void kotlin::StartFinalizerThreadIfNeeded() noexcept {
    mm::GlobalData::Instance().gc().StartFinalizerThreadIfNeeded();
}

bool kotlin::FinalizersThreadIsRunning() noexcept {
    return mm::GlobalData::Instance().gc().FinalizersThreadIsRunning();
}

RUNTIME_NOTHROW ALWAYS_INLINE extern "C" void Kotlin_processObjectInMark(void* state, ObjHeader* object) {
    gc::GC::processObjectInMark(state, object);
}

RUNTIME_NOTHROW ALWAYS_INLINE extern "C" void Kotlin_processArrayInMark(void* state, ObjHeader* object) {
    gc::GC::processArrayInMark(state, object->array());
}

RUNTIME_NOTHROW ALWAYS_INLINE extern "C" void Kotlin_processFieldInMark(void* state, ObjHeader* field) {
    gc::GC::processFieldInMark(state, field);
}

RUNTIME_NOTHROW ALWAYS_INLINE extern "C" void Kotlin_processEmptyObjectInMark(void* state, ObjHeader* object) {
    // Empty object. Nothing to do.
    // TODO: Try to generate it in the code generator.
}

RUNTIME_NOTHROW ALWAYS_INLINE extern "C" void Kotlin_onAllocation(size_t size) {
    mm::GlobalData::Instance().gcScheduler().onAllocation(size);
}

RUNTIME_NOTHROW ALWAYS_INLINE extern "C" void Kotlin_onDeallocation(size_t size) {
    mm::GlobalData::Instance().gcScheduler().onDeallocation(size);
}
