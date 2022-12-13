/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.fir.*
import java.io.File
import java.text.DecimalFormat

data class ComponentDiffResult(
    val k1Time: Long,
    val k2Time: Long,
    val diff: Double
)

const val TotalKey = "Total"
val PercentFormat = DecimalFormat("##.##%")

fun main() {
    val k1PerfResult = readResult(false)
    val k2PerfResult = readResult(true)
    val diff = calculateDiff(k1PerfResult, k2PerfResult)
    printDiff(diff)
}

fun readResult(useK2: Boolean): Map<String, CumulativeTime> {
    val file = File("/home/ivankochurkin/Documents/JetBrains/performance/" + (if (useK2) "k2" else "k1") + "-performance-report.json")
    val json = file.readText()
    return Json.decodeFromString(json)
}

private fun calculateDiff(
    k1PerfResult: Map<String, CumulativeTime>,
    k2PerfResult: Map<String, CumulativeTime>
): MutableMap<String, Map<String, ComponentDiffResult>> {
    val diff = mutableMapOf<String, Map<String, ComponentDiffResult>>()

    val allKeys = (k1PerfResult.keys.toList() + k2PerfResult.keys.toList()).toHashSet()
    for (projectKey in allKeys) {
        val resultK1 = k1PerfResult[projectKey]
        val resultK2 = k2PerfResult[projectKey]
        if (resultK1 == null || resultK2 == null) continue

        val componentsDiff = mutableMapOf<String, ComponentDiffResult>()
        var totalK1 = 0L
        var totalK2 = 0L
        for (componentKey in resultK1.components.keys) {
            val k1 = resultK1.components[componentKey] ?: continue
            val k2 = resultK2.components[componentKey] ?: continue
            componentsDiff[componentKey] = ComponentDiffResult(k1, k2, (k2 - k1).toDouble() / k1)
            totalK1 += k1
            totalK2 += k2
        }
        componentsDiff[TotalKey] = ComponentDiffResult(totalK1, totalK2, (totalK2 - totalK1).toDouble() / totalK1)

        diff[projectKey] = componentsDiff
    }

    return diff
}

private fun printDiff(diff: MutableMap<String, Map<String, ComponentDiffResult>>) {
    val sorted = diff.toList().sortedByDescending { it.second[TotalKey]!!.diff }
    for ((projectKey, componentsDiff) in sorted) {
        println(projectKey)

        fun RTableContext.component(componentKey: String, componentDiff: ComponentDiffResult) {
            row {
                cell(componentKey, align = LEFT)
                timeCell(componentDiff.k1Time, inputUnit = TableTimeUnit.MS)
                timeCell(componentDiff.k2Time, inputUnit = TableTimeUnit.MS)
                cell(PercentFormat.format(componentDiff.diff * 100))
            }
        }

        printTable {
            row("Project", projectKey)
            row("Phase", "K1", "K2", "Diff")
            for ((componentKey, componentDiff) in componentsDiff) {
                component(componentKey, componentDiff)
            }
            separator()
            component(TotalKey, componentsDiff[TotalKey]!!)
        }
    }
}