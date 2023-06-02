/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api

import java.io.File

public sealed interface SourcesChanges {
    /**
     * A build system doesn't know the changes, and it'll be considered as a request for non-incremental compilation
     */
    public object Unknown : SourcesChanges

    /**
     * A build system isn't able to track changes, so changes should be tracked on the incremental compiler side.
     */
    public object ToBeCalculated : SourcesChanges

    public class Known(
        public val modifiedFiles: List<File>,
        public val removedFiles: List<File>,
    ) : SourcesChanges
}