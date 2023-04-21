/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly

/**
 * [KotlinGlobalModificationService] is a central service for the invalidation of caches during/between tests.
 *
 * Implementations of this service should publish global modification events to at least the following components:
 * - [KotlinModificationTrackerFactory]
 * - [KotlinModificationSubscriptionService]
 */
public abstract class KotlinGlobalModificationService {
    /**
     * Publishes an event of global modification of the module state of all `KtModule`s. [includeBinaryModules] determines whether binary
     * modules should also be considered changed.
     */
    @TestOnly
    public abstract fun publishGlobalModuleStateModification(includeBinaryModules: Boolean = true)

    /**
     * Publishes an event of global out-of-block modification of all `KtModule`s. [includeBinaryModules] determines whether binary module
     * content should also be considered modified. The event does not invalidate module state like [publishGlobalModuleStateModification],
     * so some module structure-specific caches might persist.
     */
    @TestOnly
    public abstract fun publishGlobalOutOfBlockModification(includeBinaryModules: Boolean = true)

    public companion object {
        public fun getInstance(project: Project): KotlinGlobalModificationService =
            project.getService(KotlinGlobalModificationService::class.java)
    }
}
