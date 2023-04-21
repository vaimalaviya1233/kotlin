/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers.impl

import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.KotlinGlobalModificationListener
import org.jetbrains.kotlin.analysis.providers.KotlinModificationSubscriptionService
import org.jetbrains.kotlin.analysis.providers.KotlinModuleModificationListener

/**
 * A base implementation for [KotlinModificationSubscriptionService] with simple listener management.
 */
public abstract class KotlinModificationSubscriptionServiceBase : KotlinModificationSubscriptionService() {
    private val moduleStateModificationListeners = mutableListOf<KotlinModuleModificationListener>()
    private val moduleOutOfBlockModificationListeners = mutableListOf<KotlinModuleModificationListener>()
    private val globalModuleStateModificationListeners = mutableListOf<KotlinGlobalModificationListener>()
    private val globalOutOfBlockModificationListeners = mutableListOf<KotlinGlobalModificationListener>()

    override fun subscribeToModuleStateModification(listener: KotlinModuleModificationListener) {
        moduleStateModificationListeners.add(listener)
    }

    override fun subscribeToModuleOutOfBlockModification(listener: KotlinModuleModificationListener) {
        moduleOutOfBlockModificationListeners.add(listener)
    }

    override fun subscribeToGlobalModuleStateModification(listener: KotlinGlobalModificationListener) {
        globalModuleStateModificationListeners.add(listener)
    }

    override fun subscribeToGlobalOutOfBlockModification(listener: KotlinGlobalModificationListener) {
        globalOutOfBlockModificationListeners.add(listener)
    }

    override fun publishModuleStateModification(module: KtModule) {
        moduleStateModificationListeners.forEach { it.onModification(module) }
    }

    override fun publishModuleOutOfBlockModification(module: KtModule) {
        moduleOutOfBlockModificationListeners.forEach { it.onModification(module) }
    }

    override fun publishGlobalModuleStateModification(includeBinaryModules: Boolean) {
        globalModuleStateModificationListeners.forEach { it.onModification(includeBinaryModules) }
    }

    override fun publishGlobalOutOfBlockModification(includeBinaryModules: Boolean) {
        globalOutOfBlockModificationListeners.forEach { it.onModification(includeBinaryModules) }
    }
}
