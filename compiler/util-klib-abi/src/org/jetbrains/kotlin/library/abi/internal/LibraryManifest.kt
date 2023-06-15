/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library.abi.internal

import org.jetbrains.kotlin.library.*
import java.util.*

/**
 * Anything that can be retrieved from manifest and that might be helpful to know about the inspected KLIB.
 *
 * @property uniqueName [KLIB_PROPERTY_UNIQUE_NAME]
 * @property platform [KLIB_PROPERTY_BUILTINS_PLATFORM]
 * @property nativeTargets [KLIB_PROPERTY_NATIVE_TARGETS]
 * @property compilerVersion [KLIB_PROPERTY_COMPILER_VERSION]
 * @property abiVersion [KLIB_PROPERTY_ABI_VERSION]
 * @property libraryVersion [KLIB_PROPERTY_LIBRARY_VERSION]
 * @property irProvider [KLIB_PROPERTY_IR_PROVIDER]
 */
data class LibraryManifest(
    val uniqueName: String?,
    val platform: String?,
    val nativeTargets: SortedSet<String>,
    val compilerVersion: String?,
    val abiVersion: String?,
    val libraryVersion: String?,
    val irProvider: String?
)
