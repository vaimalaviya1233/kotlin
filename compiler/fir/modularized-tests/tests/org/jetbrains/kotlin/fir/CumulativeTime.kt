/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import kotlinx.serialization.Serializable

@Serializable
data class CumulativeTime(
    @kotlinx.serialization.Transient
    val gcInfo: Map<String, GCInfo> = mapOf(),
    val components: Map<String, Long>,
    val files: Int,
    val lines: Int
) {
    constructor() : this(emptyMap(), emptyMap(), 0, 0)

    operator fun plus(other: CumulativeTime): CumulativeTime {
        return CumulativeTime(
            (gcInfo.values + other.gcInfo.values).groupingBy { it.name }.reduce { key, accumulator, element ->
                GCInfo(key, accumulator.gcTime + element.gcTime, accumulator.collections + element.collections)
            },
            (components.toList() + other.components.toList()).groupingBy { (name, _) -> name }.fold(0L) { a, b -> a + b.second },
            files + other.files,
            lines + other.lines
        )
    }

    fun totalTime() = components.values.sum()
}