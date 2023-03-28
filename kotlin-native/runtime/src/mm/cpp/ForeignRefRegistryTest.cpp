/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "ForeignRefRegistry.hpp"

#include <condition_variable>
#include <mutex>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include "ObjectTestSupport.hpp"
#include "TestSupport.hpp"
#include "ThreadRegistry.hpp"

using namespace kotlin;

namespace {

class Waiter : private Pinned {
public:
    void allow() noexcept {
        {
            std::unique_lock guard(mutex_);
            allow_ = true;
        }
        cv_.notify_all();
    };

    void wait() noexcept {
        std::unique_lock guard(mutex_);
        cv_.wait(guard, [this] { return allow_; });
    }

private:
    bool allow_ = false;
    std::mutex mutex_;
    std::condition_variable cv_;
};

struct Payload {
    using Field = ObjHeader* Payload::*;
    static constexpr std::array<Field, 0> kFields{};
};

test_support::TypeInfoHolder typeHolder{test_support::TypeInfoHolder::ObjectBuilder<Payload>()};

class Object : public test_support::Object<Payload> {
public:
    Object() noexcept : test_support::Object<Payload>(typeHolder.typeInfo()) {}

private:
};

} // namespace

class ForeignRefRegistryTest : public testing::Test {
public:
    ~ForeignRefRegistryTest() {
        // Clean up safely.
        roots();
        all();
    }

    void publish() noexcept { mm::ThreadRegistry::Instance().CurrentThreadData()->foreignRefThreadQueue().publish(); }

    std::vector<ObjHeader*> all() noexcept {
        std::vector<ObjHeader*> result;
        auto iterable = mm::ForeignRefRegistry::instance().lockForIter();
        for (auto it = iterable.begin(); it != iterable.end();) {
            if ((*it).canBeSwept()) {
                it.EraseAndAdvance();
            } else {
                result.push_back((*it).refForTests());
                ++it;
            }
        }
        return result;
    }

    std::vector<ObjHeader*> roots() noexcept {
        std::vector<ObjHeader*> result;
        for (auto* root : mm::ForeignRefRegistry::instance().roots()) {
            result.push_back(root);
        }
        return result;
    }

    void detachRef(BackRefFromAssociatedObject& ref) noexcept {
        // Simulating full GC.
        roots(); // Processing roots which will remove ref from the roots.
        ref.detach(); // Weaks processing.
        all(); // Sweeping foreign refs.
    }
};

TEST_F(ForeignRefRegistryTest, RegisterObjCRefWithoutPublish) {
    RunInNewThread([this] {
        Object object;
        ObjHeader* obj = object.header();
        ASSERT_TRUE(obj->heap());
        BackRefFromAssociatedObject ref;
        ref.initAndAddRef(obj, true);

        EXPECT_THAT(roots(), testing::UnorderedElementsAre(obj));
        EXPECT_THAT(all(), testing::UnorderedElementsAre());

        ref.releaseRef();

        EXPECT_THAT(roots(), testing::UnorderedElementsAre());
        EXPECT_THAT(all(), testing::UnorderedElementsAre());

        detachRef(ref);

        EXPECT_THAT(roots(), testing::UnorderedElementsAre());
        EXPECT_THAT(all(), testing::UnorderedElementsAre());

        publish();

        EXPECT_THAT(roots(), testing::UnorderedElementsAre());
        EXPECT_THAT(all(), testing::UnorderedElementsAre());
    });
}

TEST_F(ForeignRefRegistryTest, RegisterObjCRef) {
    RunInNewThread([this] {
        Object object;
        ObjHeader* obj = object.header();
        ASSERT_TRUE(obj->heap());
        BackRefFromAssociatedObject ref;
        ref.initAndAddRef(obj, true);

        EXPECT_THAT(roots(), testing::UnorderedElementsAre(obj));
        EXPECT_THAT(all(), testing::UnorderedElementsAre());

        publish();

        EXPECT_THAT(roots(), testing::UnorderedElementsAre(obj));
        EXPECT_THAT(all(), testing::UnorderedElementsAre(obj));

        ref.releaseRef();

        EXPECT_THAT(roots(), testing::UnorderedElementsAre());
        EXPECT_THAT(all(), testing::UnorderedElementsAre(obj));

        detachRef(ref);

        EXPECT_THAT(roots(), testing::UnorderedElementsAre());
        EXPECT_THAT(all(), testing::UnorderedElementsAre());
    });
}

TEST_F(ForeignRefRegistryTest, TryObjCRef) {
    RunInNewThread([this] {
        Object object;
        ObjHeader* obj = object.header();
        ASSERT_TRUE(obj->heap());
        BackRefFromAssociatedObject ref;
        ref.initAndAddRef(obj, true);
        ref.releaseRef();
        publish();

        {
            ThreadStateGuard guard(ThreadState::kNative);
            EXPECT_TRUE(ref.tryAddRef<ErrorPolicy::kTerminate>());
        }

        EXPECT_THAT(roots(), testing::UnorderedElementsAre(obj));
        EXPECT_THAT(all(), testing::UnorderedElementsAre(obj));

        ref.releaseRef();

        detachRef(ref);

        EXPECT_THAT(roots(), testing::UnorderedElementsAre());
        EXPECT_THAT(all(), testing::UnorderedElementsAre());
    });
}

TEST_F(ForeignRefRegistryTest, ReRetainObjCRefBeforePublish) {
    RunInNewThread([this] {
        Object object;
        ObjHeader* obj = object.header();
        ASSERT_TRUE(obj->heap());
        BackRefFromAssociatedObject ref;
        ref.initAndAddRef(obj, true);
        ref.releaseRef();
        ref.addRef<ErrorPolicy::kTerminate>();

        EXPECT_THAT(roots(), testing::UnorderedElementsAre(obj));
        EXPECT_THAT(all(), testing::UnorderedElementsAre());

        publish();

        EXPECT_THAT(roots(), testing::UnorderedElementsAre(obj));
        EXPECT_THAT(all(), testing::UnorderedElementsAre(obj));

        ref.releaseRef();
        detachRef(ref);

        EXPECT_THAT(roots(), testing::UnorderedElementsAre());
        EXPECT_THAT(all(), testing::UnorderedElementsAre());
    });
}

TEST_F(ForeignRefRegistryTest, StressObjCRef) {
    Object object;
    ObjHeader* obj = object.header();
    ASSERT_TRUE(obj->heap());
    Waiter waiter;
    std::vector<ScopedThread> mutators;
    std::vector<BackRefFromAssociatedObject> refs(kDefaultThreadCount);
    for (int i = 0; i < kDefaultThreadCount; ++i) {
        mutators.emplace_back([&, i, this] {
            ScopedMemoryInit scope;
            waiter.wait();
            refs[i].initAndAddRef(obj, true);
            publish();
            refs[i].releaseRef();
        });
    }
    waiter.allow();
    mutators.clear();
    // Simulating full GC.
    roots();
    for (auto& ref : refs) {
        ref.detach();
    }
    all();
    EXPECT_THAT(roots(), testing::UnorderedElementsAre());
    EXPECT_THAT(all(), testing::UnorderedElementsAre());
}

TEST_F(ForeignRefRegistryTest, StressObjCRefRetainRelease) {
    RunInNewThread([this] {
        constexpr int kGCCycles = 10000;
        constexpr int kRefsCount = 3;
        Object object;
        ObjHeader* obj = object.header();
        ASSERT_TRUE(obj->heap());
        Waiter waiter;
        std::atomic<bool> canStop = false;
        std::vector<BackRefFromAssociatedObject> refs;
        refs.reserve(kRefsCount);
        for (int i = 0; i < kRefsCount; ++i) {
            auto& ref = refs.emplace_back();
            ref.initAndAddRef(obj, true);
            ref.releaseRef();
        }
        publish();
        std::vector<ScopedThread> mutators;
        mutators.emplace_back([&, this] {
            waiter.wait();
            for (int i = 0; i < kGCCycles; ++i) {
                roots();
                all();
            }
            canStop.store(true, std::memory_order_release);
        });
        for (int i = 0; i < kDefaultThreadCount; ++i) {
            mutators.emplace_back([i, &refs, &waiter, &canStop] {
                ScopedMemoryInit scope;
                waiter.wait();
                auto& ref = refs[i % kRefsCount];
                while (!canStop.load(std::memory_order_acquire)) {
                    ref.addRef<ErrorPolicy::kTerminate>();
                    ref.releaseRef();
                }
            });
        }
        waiter.allow();
        mutators.clear();
        // Simulating full GC.
        roots();
        for (auto& ref : refs) {
            ref.detach();
        }
        all();
        EXPECT_THAT(roots(), testing::UnorderedElementsAre());
        EXPECT_THAT(all(), testing::UnorderedElementsAre());
    });
}
