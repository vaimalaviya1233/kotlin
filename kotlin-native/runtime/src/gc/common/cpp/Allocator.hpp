/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <utility>

#include "GlobalData.hpp"
#include "GCScheduler.hpp"
#include "ObjectAlloc.hpp"

namespace kotlin {
namespace gc {

// TODO: Try to move from custom allocator interface to standard one.
//       Currently Free method is in the way: it is static to avoid keeping allocator state in
//       unique_ptr's deleter in ObjectFactory.

class Allocator {
public:
    void* Alloc(size_t size) noexcept { return allocateInObjectPool(size); }
    static void Free(void* instance, size_t size) noexcept { freeInObjectPool(instance, size); }
};

template <typename BaseAllocator>
class AllocatorWithGC {
public:
    explicit AllocatorWithGC(BaseAllocator base) noexcept : base_(std::move(base)) {}

    void* Alloc(size_t size) noexcept {
        if (void* ptr = base_.Alloc(size)) {
            return ptr;
        }
        // Tell GC that we failed to allocate, and try one more time.
        mm::GlobalData::Instance().gcScheduler().onOOM(size);
        return base_.Alloc(size);
    }

    static void Free(void* instance, size_t size) noexcept { BaseAllocator::Free(instance, size); }

private:
    BaseAllocator base_;
};

} // namespace gc
} // namespace kotlin
