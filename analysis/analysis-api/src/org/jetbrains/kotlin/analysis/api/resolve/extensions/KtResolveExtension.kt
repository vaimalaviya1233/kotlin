/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.resolve.extensions

import org.jetbrains.kotlin.name.FqName

/**
 * Provides a list of Kotlin files which provides additional, generated declarations for resolution.
 *
 * Provided by the [KtResolveExtensionProvider].
 *
 * All member implementations should:
 * - consider caching the results for subsequent invocations.
 * - be lightweight and not build the whole file structure inside.
 * - not use the Kotlin resolve inside, as this function is called during session initialization, so Analysis API access is forbidden.
 *
 * @see KtResolveExtensionFile
 * @see KtResolveExtensionProvider
 */
public abstract class KtResolveExtension {
    /**
     * Get the list of files that should be generated for the module. Returned files should contain valid Kotlin code.
     *
     * If the content of these files becomes invalid (e.g., because the source declarations they were based on changed), the
     * [KtResolveExtension] must publish an out-of-block modification event via the Analysis API message bus:
     * [org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics.MODULE_OUT_OF_BLOCK_MODIFICATION].
     *
     * To react to changes in Kotlin sources, [KtResolveExtension] may subscribe to Analysis API topics:
     * [org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics]. If the [KtResolveExtension] both subscribes to and
     * publishes modification events, care needs to be taken that no cycles are introduced. In general, the [KtResolveExtension] should
     * never publish an event for a module A in a listener for the same module A.
     *
     * An out-of-block modification event for the [KtResolveExtension]'s associated module does not need to be published in response to an
     * out-of-block modification event for the same module, because the original event suffices for invalidation.
     *
     * @see KtResolveExtensionFile
     * @see KtResolveExtension
     */
    public abstract fun getKtFiles(): List<KtResolveExtensionFile>

    /**
     * Returns the set of packages that are contained in the files provided by [getKtFiles].
     *
     * The returned package set should be a strict set of all file packages,
     * so `for-all pckg: pckg in getContainedPackages() <=> exists file: file in getKtFiles() && file.getFilePackageName() == pckg`
     *
     * @see KtResolveExtension
     */
    public abstract fun getContainedPackages(): Set<FqName>
}