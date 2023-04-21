/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.project.structure.KtModule

/**
 * [KotlinModificationSubscriptionService] allows listeners to subscribe to and publishers to publish modification events.
 *
 * Care needs to be taken with the lack of interplay of different channels: Publishing global modification events, for example via
 * [publishGlobalOutOfBlockModification], does not publish an event for subscribers of module-level channels such as
 * [subscribeToModuleOutOfBlockModification]. Similarly, publishing a module state modification event does not publish an event for
 * subscribers of out-of-block modification. It is generally considered best practice to subscribe to all available channels.
 *
 * Some other modification events are represented by modification trackers: [KotlinModificationTrackerFactory].
 *
 *
 * #### Implementation Notes
 *
 * In general, if your tool works with static code and static module structure, you may ignore any subscribing listeners and do not need to
 * publish events. However, keep in mind the contracts of the various subscription functions. For example, your tool may guarantee a static
 * module structure but source code can still change. In that case, [subscribeToModuleStateModification] is allowed to do nothing, but
 * [subscribeToModuleOutOfBlockModification] and [subscribeToGlobalOutOfBlockModification] need to be implemented.
 */
public abstract class KotlinModificationSubscriptionService {
    /**
     * Subscribes to module structure changes in the project. [listener] will be called with the affected [KtModule] each time the module's
     * structure changes, the module is moved or removed, and so on.
     */
    public abstract fun subscribeToModuleStateModification(listener: KotlinModuleModificationListener)

    /**
     * Publishes a module state modification event to subscribers of [subscribeToModuleStateModification].
     */
    public abstract fun publishModuleStateModification(module: KtModule)

    /**
     * Subscribes to out-of-block modification happening in the project. [listener] will be called with the affected [KtModule] each time an
     * OOBM happens in the module's source code.
     *
     * See [KotlinModificationTrackerFactory.createProjectWideOutOfBlockModificationTracker] for an explanation of out-of-block
     * modifications.
     *
     *
     * #### Implementation Notes
     *
     * You may choose to publish an OOBM event for any and all source code changes, not just out-of-block modifications, to simplify the
     * implementation.
     */
    public abstract fun subscribeToModuleOutOfBlockModification(listener: KotlinModuleModificationListener)

    /**
     * Publishes a module out-of-block modification event to subscribers of [subscribeToModuleOutOfBlockModification].
     */
    public abstract fun publishModuleOutOfBlockModification(module: KtModule)

    /**
     * Subscribes to global modification of the module state of all [KtModule]s. [listener] will be called with a flag
     * `includeBinaryModules`, which determines whether binary module state is also considered modified.
     *
     * Usually, this event is published to invalidate caches during/between tests.
     */
    public abstract fun subscribeToGlobalModuleStateModification(listener: KotlinGlobalModificationListener)

    /**
     * Publishes a global module state modification event to subscribers of [subscribeToGlobalModuleStateModification].
     */
    public abstract fun publishGlobalModuleStateModification(includeBinaryModules: Boolean)

    /**
     * Subscribes to global out-of-block modification of all [KtModule]s. [listener] will be called with a flag `includeBinaryModules`,
     * which determines whether binary module content is also considered modified.
     *
     * Usually, this event is published on global out-of-block modification and to invalidate caches during/between tests.
     */
    public abstract fun subscribeToGlobalOutOfBlockModification(listener: KotlinGlobalModificationListener)

    /**
     * Publishes a global out-of-block modification event to subscribers of [subscribeToGlobalOutOfBlockModification].
     */
    public abstract fun publishGlobalOutOfBlockModification(includeBinaryModules: Boolean)

    public companion object {
        public fun getInstance(project: Project): KotlinModificationSubscriptionService =
            project.getService(KotlinModificationSubscriptionService::class.java)
    }
}

public fun interface KotlinModuleModificationListener {
    /**
     * A [module] has been modified. The concrete meaning of this event depends on the contract of the subscription function this listener
     * was registered with.
     */
    public fun onModification(module: KtModule)
}

public fun interface KotlinGlobalModificationListener {
    /**
     * All modules have been modified. [includeBinaryModules] determines whether binary modules should also be considered modified. The
     * concrete meaning of this event depends on the contract of the subscription function this listener was registered with.
     */
    public fun onModification(includeBinaryModules: Boolean)
}
