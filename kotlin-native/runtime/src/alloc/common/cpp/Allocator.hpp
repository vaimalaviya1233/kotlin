/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <cstddef>

#include "ExtraObjectData.hpp"
#include "GCScheduler.hpp"
#include "GCStatistics.hpp"
#include "Memory.h"
#include "Utils.hpp"
#include "std_support/Memory.hpp"

namespace kotlin::alloc {

class Allocator : private Pinned {
public:
    class Impl;

    class ThreadData : private Pinned {
    public:
        class Impl;

        explicit ThreadData(Allocator& owner, gcScheduler::GCSchedulerThreadData& gcScheduler) noexcept;
        ~ThreadData();

        Impl& impl() noexcept { return *impl_; }

        void publish() noexcept;
        void clearForTests() noexcept;

        ObjHeader* allocateObject(const TypeInfo* typeInfo) noexcept;
        ArrayHeader* allocateArray(const TypeInfo* typeInfo, uint32_t elements) noexcept;
        mm::ExtraObjectData& allocateExtraObjectDataForObject(ObjHeader* header, TypeInfo* typeInfo) noexcept;
        void destroyExtraObjectData(mm::ExtraObjectData& data) noexcept;
        void destroyExtraObjectData2(mm::ExtraObjectData& data) noexcept;

    private:
        std_support::unique_ptr<Impl> impl_;
    };

    class GCContext : private MoveOnly {
    public:
        class Impl;

        GCContext(Allocator& allocator, gc::GCHandle gcHandle) noexcept;
        ~GCContext();

        Impl& impl() noexcept { return *impl_; }

        void sweepExtraObjects() noexcept;
        void sweep() noexcept;

    private:
        // TODO: That's heap allocation during GC.
        std_support::unique_ptr<Impl> impl_;
    };

    Allocator() noexcept;
    ~Allocator();

    Impl& impl() noexcept { return *impl_; }

    void setFinalizerCompletion(std::function<void(int64_t)> f) noexcept;

    static size_t GetAllocatedHeapSize(ObjHeader* object) noexcept;

    size_t GetHeapObjectsCountUnsafe() noexcept;
    size_t GetTotalHeapObjectsSizeUnsafe() noexcept;
    size_t GetExtraObjectsCountUnsafe() noexcept;
    size_t GetTotalExtraObjectsSizeUnsafe() noexcept;

    static ObjHeader* objectForData(void* data) noexcept;
    static void* dataForObject(ObjHeader* object) noexcept;

    void StartFinalizerThreadIfNeeded() noexcept;
    void StopFinalizerThreadIfRunning() noexcept;
    bool FinalizersThreadIsRunning() noexcept;

    void clearForTests() noexcept;

    GCContext prepareForGC(gc::GCHandle gcHandle) noexcept {
        return GCContext(*this, gcHandle);
    }

private:
    std_support::unique_ptr<Impl> impl_;
};

}
