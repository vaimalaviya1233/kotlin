/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "GCSchedulerConfig.hpp"
#include "Mutex.hpp"
#include "Utils.hpp"
#include "std_support/Memory.hpp"
#include "std_support/Optional.hpp"

namespace kotlin::gc {
class GC;
}

namespace kotlin::gcScheduler {

class GCScheduler : private Pinned {
public:
    class Impl;

    explicit GCScheduler(gc::GC& gc) noexcept;
    ~GCScheduler();

    template <typename F>
    auto readConfig(F&& f) noexcept(noexcept(f(std::declval<const GCSchedulerConfig&>()))) {
        std::unique_lock guard(configMutex_);
        return f(config_);
    }

    template <typename F>
    void modifyConfig(F&& f) noexcept(noexcept(f(std::declval<GCSchedulerConfig&>()))) {
        std::unique_lock guard(configMutex_);
        f(config_);
        // TODO: Scheduler implementations should update their stuff in a global pause.
        //       To make the update as prompt as possible, thread suspensions should be queueable.
    }

    // Called by different mutator threads.
    // TODO: Separate scheduling and waiting maybe?
    void schedule() noexcept;
    void scheduleAndWaitFullGC() noexcept;
    void scheduleAndWaitFullGCWithFinalizers() noexcept;

    // Called by different mutator threads via allocator.
    void onAllocation(uint64_t allocatedBytes) noexcept;
    void onOOM(uint64_t size) noexcept;

    void onDeallocation(uint64_t allocatedBytes) noexcept;

    // Called by different mutator threads.
    void onSafePoint() noexcept;

private:
    Mutex<MutexThreadStateHandling::kSwitchIfRegistered> configMutex_;
    GCSchedulerConfig config_;
    std::optional<GCSchedulerConfig> pendingConfig_;
    std_support::unique_ptr<Impl> impl_;
};

}
