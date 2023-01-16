/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef RUNTIME_GC_NOOP_NOOP_GC_H
#define RUNTIME_GC_NOOP_NOOP_GC_H

#include <cstddef>

#include "Allocator.hpp"
#include "GCStatistics.hpp"
#include "Logging.hpp"
#include "ObjectFactory.hpp"
#include "Utils.hpp"
#include "Types.h"

namespace kotlin {

namespace mm {
class ThreadData;
}

namespace gc {

// No-op GC is a GC that does not free memory.
// TODO: It can be made more efficient.
class NoOpGC : private Pinned {
public:
    class ObjectData {};

    using Allocator = gc::Allocator;

    class ThreadData : private Pinned {
    public:
        using ObjectData = NoOpGC::ObjectData;

        ThreadData(NoOpGC& gc, mm::ThreadData& threadData) noexcept {}
        ~ThreadData() = default;
    private:
    };

    explicit NoOpGC(mm::ObjectFactory<NoOpGC>&) noexcept {
        RuntimeLogDebug({kTagGC}, "No-op GC initialized");
    }
    ~NoOpGC() = default;

    void RunGC(GCHandle& handle) noexcept {
        handle.started();
        handle.finished();
        handle.finalizersDone();
    }
};

} // namespace gc
} // namespace kotlin

#endif // RUNTIME_GC_NOOP_NOOP_GC_H
