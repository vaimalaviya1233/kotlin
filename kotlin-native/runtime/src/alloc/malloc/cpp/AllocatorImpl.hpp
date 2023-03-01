/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "Allocator.hpp"

#include "AllocatorWithGC.hpp"
#include "ExtraObjectDataFactory.hpp"
#include "FinalizerProcessor.hpp"
#include "GC.hpp"
#include "ObjectFactory.hpp"
#include "ThreadSuspension.hpp"

namespace kotlin::alloc {

struct ObjectFactoryTraits {};

class Allocator::Impl {
public:
  struct ObjectFactoryAllocatorTraits {};
  using ObjectFactoryAllocator = AllocatorWithGC<BaseAllocator, Allocator::ThreadData::Impl>;
  using ObjectFactory = alloc::ObjectFactory<ObjectFactoryAllocator>;
  using FinalizerQueue = ObjectFactory::FinalizerQueue;
  using FinalizerQueueTraits = ObjectFactory::FinalizerQueueTraits;

  Impl() noexcept : finalizerProcessor_([this](int64_t epoch) noexcept { OnFinalizerCompletion(epoch); }) {
  }

  ExtraObjectDataFactory& extraObjectDataFactory() noexcept { return extraObjectDataFactory_; }

  ObjectFactory& objectFactory() noexcept { return objectFactory_; }

  FinalizerProcessor<FinalizerQueue, FinalizerQueueTraits>& finalizerProcessor() noexcept { return finalizerProcessor_; }

  std::function<void(int64_t)>& finalizerCompletion() noexcept { return finalizerCompletion_; }

private:
  void OnFinalizerCompletion(int64_t epoch) noexcept {
    return finalizerCompletion_(epoch);
  }

  std::function<void(int64_t)> finalizerCompletion_;
  ExtraObjectDataFactory extraObjectDataFactory_;
  ObjectFactory objectFactory_;
  FinalizerProcessor<FinalizerQueue, FinalizerQueueTraits> finalizerProcessor_;
};

class Allocator::ThreadData::Impl {
public:
  explicit Impl(Allocator::Impl& owner, gcScheduler::GCSchedulerThreadData& gcScheduler) noexcept : gcScheduler_(gcScheduler), extraObjectDataFactory_(owner.extraObjectDataFactory()), objectFactoryAllocator_(BaseAllocator(), *this), objectFactory_(owner.objectFactory(), objectFactoryAllocator_) {}

  ExtraObjectDataFactory::ThreadQueue& extraObjectDataFactory() noexcept { return extraObjectDataFactory_; }

  Allocator::Impl::ObjectFactory::ThreadQueue& objectFactory() noexcept { return objectFactory_; }

  void SafePointAllocation(size_t size) noexcept {
      gcScheduler_.OnSafePointAllocation(size);
      // TODO: Let's not make every allocation a safepoint? We have enough safepoints already.
      mm::SuspendIfRequested();
  }

  void OnOOM(size_t size) noexcept {
      TODO();
  }

private:
  gcScheduler::GCSchedulerThreadData& gcScheduler_;
  ExtraObjectDataFactory::ThreadQueue extraObjectDataFactory_;
  Allocator::Impl::ObjectFactoryAllocator objectFactoryAllocator_;
  Allocator::Impl::ObjectFactory::ThreadQueue objectFactory_;
};

class Allocator::GCContext::Impl {
public:
    Impl(gc::GCHandle gcHandle, Allocator::Impl& allocator) noexcept : gcHandle_(gcHandle), allocator_(allocator), extraObjectDataFactory_(allocator.extraObjectDataFactory().LockForIter()), objectFactory_(allocator.objectFactory().LockForIter()) {
    }

    ~Impl() {
        gcHandle_.finalizersScheduled(finalizerQueue_.size());
        allocator_.finalizerProcessor().ScheduleTasks(std::move(finalizerQueue_), gcHandle_.getEpoch());
    }

    void sweep() noexcept {
        finalizerQueue_ = objectFactory_.Sweep(gcHandle_, [](ObjHeader* object) noexcept {
            return gc::GC::tryResetMark(object);
        });
        kotlin::compactObjectPoolInMainThread();
    }

    void sweepExtraObjects() noexcept {
      extraObjectDataFactory_.Sweep(gcHandle_);
    }

private:
    gc::GCHandle gcHandle_;
    Allocator::Impl& allocator_;
    Allocator::Impl::FinalizerQueue finalizerQueue_;
    ExtraObjectDataFactory::Iterable extraObjectDataFactory_;
    Allocator::Impl::ObjectFactory::Iterable objectFactory_;
};

}
