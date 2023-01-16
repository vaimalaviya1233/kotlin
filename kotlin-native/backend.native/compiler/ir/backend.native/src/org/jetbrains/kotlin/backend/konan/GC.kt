/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

enum class GC(val shorthand: String?) {
    NOOP(null),
    STOP_THE_WORLD_MARK_AND_SWEEP("stw"),
    PARALLEL_MARK_CONCURRENT_SWEEP("pmcs"),
}
