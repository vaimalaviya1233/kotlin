/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "Allocator.hpp"

#include "CustomAllocator.hpp"
#include "CustomFinalizerProcessor.hpp"
#include "FinalizerProcessor.hpp"
#include "GC.hpp"
#include "Heap.hpp"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"
#include "ThreadSuspension.hpp"

namespace kotlin::alloc {

struct ObjectFactoryTraits {};

class Allocator::Impl {
public:
  Impl() noexcept : finalizerProcessor_([this](int64_t epoch) noexcept { OnFinalizerCompletion(epoch); }) {
  }

  FinalizerProcessor<FinalizerQueue, FinalizerQueueTraits>& finalizerProcessor() noexcept { return finalizerProcessor_; }

  std::function<void(int64_t)>& finalizerCompletion() noexcept { return finalizerCompletion_; }

  Heap& heap() noexcept { return heap_; }

private:
  void OnFinalizerCompletion(int64_t epoch) noexcept {
    return finalizerCompletion_(epoch);
  }

  std::function<void(int64_t)> finalizerCompletion_;
  FinalizerProcessor<FinalizerQueue, FinalizerQueueTraits> finalizerProcessor_;
  Heap heap_;
};

class Allocator::ThreadData::Impl {
public:
  explicit Impl(Allocator::Impl& owner, gcScheduler::GCSchedulerThreadData& gcScheduler) noexcept : gcScheduler_(gcScheduler), allocator_(owner.heap(), gcScheduler) {}

  CustomAllocator& allocator() noexcept { return allocator_; }

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
  CustomAllocator allocator_;
};

class Allocator::GCContext::Impl {
public:
    Impl(gc::GCHandle gcHandle, Allocator::Impl& allocator) noexcept : gcHandle_(gcHandle), allocator_(allocator) {
      for (auto& thread : mm::ThreadRegistry::Instance().LockForIter()) {
          thread.allocator().impl().allocator().PrepareForGC();
      }
      allocator_.heap().PrepareForGC();
    }

    ~Impl() {
        gcHandle_.finalizersScheduled(finalizerQueue_.size());
        allocator_.finalizerProcessor().ScheduleTasks(std::move(finalizerQueue_), gcHandle_.getEpoch());
    }

    void sweep() noexcept {
      allocator_.heap().Sweep();
    }

    void sweepExtraObjects() noexcept {
      finalizerQueue_ = allocator_.heap().SweepExtraObjects(gcHandle_);
    }

private:
    gc::GCHandle gcHandle_;
    Allocator::Impl& allocator_;
    FinalizerQueue finalizerQueue_;
};

}
