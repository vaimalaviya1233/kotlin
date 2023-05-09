/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testbase

import org.gradle.api.logging.LogLevel
import org.gradle.testkit.runner.BuildResult
import org.intellij.lang.annotations.Language

@Language("RegExp")
private fun taskOutputRegexForDebugLog(
    taskName: String,
) = """
    \[org\.gradle\.internal\.operations\.DefaultBuildOperationRunner] Build operation 'Task $taskName' started
    ([\s\S]+?)
    \[org\.gradle\.internal\.operations\.DefaultBuildOperationRunner] Build operation 'Task $taskName' completed
    """.trimIndent()
    .replace("\n", "")
    .toRegex()

@Language("RegExp")
private fun taskOutputRegexForInfoLog(
    taskName: String,
) = """
    ^\s*$
    ^> Task $taskName$
    ([\s\S]+?)
    ^\s*$
    """.trimIndent()
    .toRegex(RegexOption.MULTILINE)

/**
 * Gets the output produced by a specific task during a Gradle build.
 *
 * @param taskPath The path of the task whose output should be retrieved.
 * @param infoLog The given output contains no more than the [LogLevel.INFO] level logs.
 *                  Defaults to false, which means that output must contain [LogLevel.DEBUG] level logs.
 *
 * @return The output produced by the specified task during the build.
 *
 * @throws IllegalStateException if the specified task path does not match any tasks in the build.
 */
fun BuildResult.getOutputForTask(taskPath: String, infoLog: Boolean = false): String =
    getOutputForTask(taskPath, output, infoLog)

/**
 * Gets the output produced by a specific task during a Gradle build.
 *
 * @param taskPath The path of the task whose output should be retrieved.
 * @param output The output from which we should extract task's output
 * @param infoLog The given output contains no more than the [LogLevel.INFO] level logs.
 *                  Defaults to false, which means that output must contain [LogLevel.DEBUG] level logs.
 *
 * @return The output produced by the specified task during the build.
 *
 * @throws IllegalStateException if the specified task path does not match any tasks in the build.
 */
fun getOutputForTask(taskPath: String, output: String, infoLog: Boolean = false): String = (
        if (infoLog) taskOutputRegexForInfoLog(taskPath)
        else taskOutputRegexForDebugLog(taskPath)
        )
    .find(output)
    ?.let { it.groupValues[1] }
    ?: error("Could not find output for task $taskPath")
