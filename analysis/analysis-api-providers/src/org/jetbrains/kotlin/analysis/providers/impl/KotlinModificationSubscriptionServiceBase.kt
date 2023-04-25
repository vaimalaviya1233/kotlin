/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers.impl

import org.jetbrains.kotlin.analysis.providers.KotlinModificationEvent
import org.jetbrains.kotlin.analysis.providers.KotlinModificationListener
import org.jetbrains.kotlin.analysis.providers.KotlinModificationSubscriptionService

/**
 * A base implementation for [KotlinModificationSubscriptionService] with simple listener management.
 */
public abstract class KotlinModificationSubscriptionServiceBase : KotlinModificationSubscriptionService() {
    private val listeners = mutableListOf<KotlinModificationListener>()

    override fun subscribe(listener: KotlinModificationListener) {
        listeners.add(listener)
    }

    override fun publish(event: KotlinModificationEvent) {
        listeners.forEach { it.onModification(event) }
    }
}
