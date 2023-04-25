/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.project.structure.KtModule

/**
 * [KotlinModificationSubscriptionService] allows listeners to subscribe to and publishers to publish [KotlinModificationEvent]s.
 *
 * Care needs to be taken with the lack of interplay between different types of events: Publishing a global modification event, for example,
 * does not imply the corresponding module-level modification and does not raise such an event. Similarly, publishing a module state
 * modification event does not imply out-of-block modification. Event listeners should thus react appropriately to all possible
 * [KotlinModificationEvent]s.
 *
 * Further modification tracking is implemented by modification trackers: [KotlinModificationTrackerFactory].
 *
 *
 * #### Implementation Notes
 *
 * In general, if your tool works with static code and static module structure, you may ignore any subscribing listeners and do not need to
 * publish events. However, keep in mind the contracts of the various events. For example, your tool could guarantee a static module
 * structure but source code can still change. In that case, [KotlinModuleStateModificationEvent]s can be ignored (yet not necessarily
 * [KotlinGlobalModuleStateModificationEvent], for test support), but [KotlinModuleOutOfBlockModificationEvent]s and
 * [KotlinGlobalOutOfBlockModificationEvent]s need to be supported.
 */
public abstract class KotlinModificationSubscriptionService {
    /**
     * Subscribes to [KotlinModificationEvent]s published via [publish].
     */
    public abstract fun subscribe(listener: KotlinModificationListener)

    /**
     * Publishes [event] to all listeners registered via [subscribe].
     */
    public abstract fun publish(event: KotlinModificationEvent)

    /**
     * Publishes a [KotlinModuleStateModificationEvent] to all listeners.
     */
    public fun publishModuleStateModification(module: KtModule) {
        publish(KotlinModuleStateModificationEvent(module))
    }

    /**
     * Publishes a [KotlinModuleOutOfBlockModificationEvent] to all listeners.
     */
    public fun publishModuleOutOfBlockModification(module: KtModule) {
        publish(KotlinModuleOutOfBlockModificationEvent(module))
    }

    /**
     * Publishes a [KotlinGlobalModuleStateModificationEvent] to all listeners.
     */
    public fun publishGlobalModuleStateModification(includeBinaryModules: Boolean) {
        publish(KotlinGlobalModuleStateModificationEvent(includeBinaryModules))
    }

    /**
     * Publishes a [KotlinGlobalOutOfBlockModificationEvent] to all listeners.
     */
    public fun publishGlobalOutOfBlockModification(includeBinaryModules: Boolean) {
        publish(KotlinGlobalOutOfBlockModificationEvent(includeBinaryModules))
    }

    public companion object {
        public fun getInstance(project: Project): KotlinModificationSubscriptionService =
            project.getService(KotlinModificationSubscriptionService::class.java)
    }
}

public fun interface KotlinModificationListener {
    public fun onModification(event: KotlinModificationEvent)
}
