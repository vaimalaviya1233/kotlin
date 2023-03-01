/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "GCScheduler.hpp"
#include "Memory.h"
#include "Types.h"
#include "Utils.hpp"
#include "std_support/Memory.hpp"

namespace kotlin {

namespace alloc {
class Allocator;
}

namespace mm {
class ThreadData;
}

namespace gc {

class GC : private Pinned {
public:
    static const size_t objectDataSize;
    static const size_t objectDataAlignment;

    class Impl;

    class ThreadData : private Pinned {
    public:
        class Impl;

        ThreadData(GC& gc, gcScheduler::GCSchedulerThreadData& gcScheduler, mm::ThreadData& threadData) noexcept;
        ~ThreadData();

        Impl& impl() noexcept { return *impl_; }

        void SafePointFunctionPrologue() noexcept;
        void SafePointLoopBody() noexcept;

        void Schedule() noexcept;
        void ScheduleAndWaitFullGC() noexcept;
        void ScheduleAndWaitFullGCWithFinalizers() noexcept;

        void Publish() noexcept;
        void ClearForTests() noexcept;

        void OnSuspendForGC() noexcept;

    private:
        std_support::unique_ptr<Impl> impl_;
    };

    GC(gcScheduler::GCScheduler& gcScheduler, alloc::Allocator& allocator) noexcept;
    ~GC();

    Impl& impl() noexcept { return *impl_; }

    void ClearForTests() noexcept;

    // Only makes sense during mark or sweep phase.
    static bool isMarked(ObjHeader* object) noexcept;
    // Only makes sense during sweep phase. Returns true if the mark bit was set.
    static bool tryResetMark(ObjHeader* object) noexcept;
    // Only makes sense during mark phase.
    static void keepAlive(ObjHeader* object) noexcept;

    static void processObjectInMark(void* state, ObjHeader* object) noexcept;
    static void processArrayInMark(void* state, ArrayHeader* array) noexcept;
    static void processFieldInMark(void* state, ObjHeader* field) noexcept;

    // TODO: This should be moved into the scheduler.
    void Schedule() noexcept;

private:
    std_support::unique_ptr<Impl> impl_;
};

inline constexpr bool kSupportsMultipleMutators = true;

} // namespace gc
} // namespace kotlin
