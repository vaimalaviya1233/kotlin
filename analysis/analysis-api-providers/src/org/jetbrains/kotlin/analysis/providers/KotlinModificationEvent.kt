/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers

import org.jetbrains.kotlin.analysis.project.structure.KtModule

/**
 * A modification event published and subscribed to via [KotlinModificationSubscriptionService].
 */
public sealed interface KotlinModificationEvent

public sealed interface KotlinModuleModificationEvent : KotlinModificationEvent {
    /**
     * The [KtModule] that has been modified.
     */
    val module: KtModule
}

public sealed interface KotlinGlobalModificationEvent : KotlinModificationEvent {
    /**
     * [includeBinaryModules] determines whether binary modules should also be considered modified.
     */
    val includeBinaryModules: Boolean
}

/**
 * This event occurs when [module]'s structure changes, when it is moved or removed, and so on.
 */
public data class KotlinModuleStateModificationEvent(override val module: KtModule) : KotlinModuleModificationEvent

/**
 * This event occurs when an out-of-block modification happens in [module]'s source code.
 *
 * See [KotlinModificationTrackerFactory.createProjectWideOutOfBlockModificationTracker] for an explanation of out-of-block
 * modifications.
 *
 * This event may be published for any and all source code changes, not just out-of-block modifications, to simplify the implementation of
 * modification detection.
 */
public data class KotlinModuleOutOfBlockModificationEvent(override val module: KtModule) : KotlinModuleModificationEvent

/**
 * This event occurs on global modification of the module state of all [KtModule]s.
 *
 * Usually, this event is published to invalidate caches during/between tests.
 */
public data class KotlinGlobalModuleStateModificationEvent(override val includeBinaryModules: Boolean) : KotlinGlobalModificationEvent

/**
 * This event occurs on global out-of-block modification of all [KtModule]s.
 *
 * Usually, this event is published on global PSI changes, and to invalidate caches during/between tests.
 */
public data class KotlinGlobalOutOfBlockModificationEvent(override val includeBinaryModules: Boolean) : KotlinGlobalModificationEvent
