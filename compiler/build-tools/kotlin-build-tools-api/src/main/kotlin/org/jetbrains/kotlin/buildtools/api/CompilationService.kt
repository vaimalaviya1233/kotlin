/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api

import org.jetbrains.kotlin.buildtools.api.jvm.ClasspathEntrySnapshot
import org.jetbrains.kotlin.buildtools.api.jvm.JvmCompilationConfig
import java.io.File

/**
 * A facade for invoking compilation and related stuff (such as [calculateClasspathSnapshot]) in Kotlin compiler.
 *
 * This interface is not intended to be implemented by API consumers. An instance of [CompilationService] is expected to be obtained from [loadImplementation].
 */
public interface CompilationService {
    /**
     * TODO KT-57565
     */
    public fun calculateClasspathSnapshot(classpathEntry: File): ClasspathEntrySnapshot

    /**
     * Provides a default [CompilerExecutionStrategyConfig] allowing to use it as is or customizing for specific requirements.
     * Could be used as an overview to default values of the options (as they are implementation-specific).
     */
    public fun generateCompilerExecutionStrategyConfig(): CompilerExecutionStrategyConfig

    /**
     * Provides a default [CompilerExecutionStrategyConfig] allowing to use it as is or customizing for specific requirements.
     * Could be used as an overview to default values of the options (as they are implementation-specific).
     */
    public fun generateJvmCompilationConfig(): JvmCompilationConfig

    /**
     * Compiles Kotlin code targeting JVM platform and using specified options.
     * @param strategyConfig an instance of [CompilerExecutionStrategyConfig] initially obtained from [generateCompilerExecutionStrategyConfig]
     * @param compilationConfig an instance of [JvmCompilationConfig] initially obtained from [generateJvmCompilationConfig]
     * @param sources a set of all sources of the compilation unit
     * @param arguments a list of Kotlin JVM compiler arguments
     */
    public fun compileJvm(
        strategyConfig: CompilerExecutionStrategyConfig,
        compilationConfig: JvmCompilationConfig,
        sources: Set<File>,
        arguments: List<String>
    )

    public companion object {
        @JvmStatic
        public fun loadImplementation(classLoader: ClassLoader): CompilationService =
            loadImplementation(CompilationService::class, classLoader)
    }
}