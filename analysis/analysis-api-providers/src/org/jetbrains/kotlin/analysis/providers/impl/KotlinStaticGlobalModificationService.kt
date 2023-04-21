/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers.impl

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.providers.KotlinGlobalModificationService
import org.jetbrains.kotlin.analysis.providers.KotlinModificationSubscriptionService

public class KotlinStaticGlobalModificationService(private val project: Project) : KotlinGlobalModificationService() {
    override fun publishGlobalModuleStateModification(includeBinaryModules: Boolean) {
        KotlinStaticModificationTrackerFactory.getInstance(project).incrementModificationsCount(includeBinaryModules)
        KotlinModificationSubscriptionService.getInstance(project).publishGlobalModuleStateModification(includeBinaryModules)
    }

    @TestOnly
    override fun publishGlobalOutOfBlockModification(includeBinaryModules: Boolean) {
        KotlinStaticModificationTrackerFactory.getInstance(project).incrementModificationsCount(includeBinaryModules)
        KotlinModificationSubscriptionService.getInstance(project).publishGlobalOutOfBlockModification(includeBinaryModules)
    }
}
