/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.internal

import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.CompilerExecutionStrategyConfig
import org.jetbrains.kotlin.buildtools.api.jvm.*
import java.io.File

internal class CompilationServiceImpl : CompilationService {
    override fun calculateClasspathSnapshot(classpathEntry: File): ClasspathEntrySnapshot {
        TODO("Calculating classpath snapshots via the Build Tools API is not yet implemented: KT-57565")
    }

    override fun generateCompilerExecutionStrategyConfig() = CompilerExecutionStrategyConfigImpl()

    override fun generateJvmCompilationConfig() = JvmCompilationConfigImpl()

    override fun compileJvm(
        strategyConfig: CompilerExecutionStrategyConfig,
        compilationConfig: JvmCompilationConfig,
        sources: Set<File>,
        arguments: List<String>
    ) {
        println("I'm simulating compilation, nothing more yet")
    }
}