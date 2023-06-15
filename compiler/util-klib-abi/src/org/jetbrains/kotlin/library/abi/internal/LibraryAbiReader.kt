/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library.abi.internal

import org.jetbrains.kotlin.library.abi.internal.impl.MockLibraryAbiReader
import java.io.File

object LibraryAbiReader {
    /**
     * Inspect the KLIB at [library]. The KLIB can be either in a directory (unzipped) or in a file (zipped) form.
     */
    fun readAbiInfo(library: File): LibraryAbi = MockLibraryAbiReader.readAbiInfo(library) // TODO: replace by a real implementation
}
