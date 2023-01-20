/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "GC.hpp"
#include "GCStatistics.hpp"
#include "GCState.hpp"
#include "ScopedThread.hpp"

namespace kotlin::gcScheduler::internal {

template <typename GCThreadDelegate>
class GCThread final : private gc::GCStateHolder::Delegate {
public:
    GCThread(gc::GC& gc, GCThreadDelegate& delegate) noexcept :
        delegate_(delegate),
        state_(*this),
        gc_(gc),
        gcThread_(ScopedThread::attributes().name("GC thread"), &GCThread::routine, this) {
            gc::GCHandle::SetGlobalGCStateHolder(&state_);
        }

    ~GCThread() {
        state_.shutdown();
    }

    gc::GCStateHolder& state() noexcept {
        return state_;
    }

private:
    void onStartedEpoch(int64_t epoch) noexcept {
        auto handle = gc::GCHandle::getByEpoch(epoch);
        delegate_.onGCStarted(handle);
    }

    void onFinishedEpoch(int64_t epoch) noexcept {
        auto handle = gc::GCHandle::getByEpoch(epoch);
        delegate_.onGCDidFinish(handle);
    }

    void onFinalizedEpoch(int64_t epoch) noexcept {}

    void routine() noexcept {
        while (true) {
            auto epoch = state_.waitScheduled();
            if (epoch.has_value()) {
                auto handle = gc::GCHandle::create(*epoch);
                gc_.RunGC(handle);
            } else {
                return;
            }
        }
    }

    GCThreadDelegate& delegate_;
    gc::GCStateHolder state_;
    gc::GC& gc_;
    ScopedThread gcThread_;
};

}
