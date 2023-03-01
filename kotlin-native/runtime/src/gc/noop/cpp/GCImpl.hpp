/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "GC.hpp"

#include "NoOpGC.hpp"

namespace kotlin {
namespace gc {

using GCImpl = NoOpGC;

class GC::Impl : private Pinned {
public:
    Impl() noexcept = default;

    GCImpl& gc() noexcept { return gc_; }

private:
    GCImpl gc_;
};

class GC::ThreadData::Impl : private Pinned {
public:
    Impl() noexcept = default;

    GCImpl::ThreadData& gc() noexcept { return gc_; }

private:
    GCImpl::ThreadData gc_;
};

} // namespace gc
} // namespace kotlin
