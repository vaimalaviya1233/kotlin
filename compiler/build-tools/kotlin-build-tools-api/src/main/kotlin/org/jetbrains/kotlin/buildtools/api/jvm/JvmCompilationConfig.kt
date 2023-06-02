/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api.jvm

import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.buildtools.api.SourcesChanges
import java.io.File

/**
 * A configurator of compilation options.
 * This interface defines a set of properties and methods that allow users to customize the compilation process.
 * It provides control over various aspects of compilation, such as incremental compilation, logging customization and other.
 *
 * This interface is not intended to be implemented by API consumers.
 */
public interface JvmCompilationConfig {
    /**
     * A logger used during the compilation.
     *
     * Default logger is a logger just printing messages to stdin and stderr.
     */
    public val logger: KotlinLogger

    /**
     * @see [logger]
     */
    public fun useLogger(logger: KotlinLogger): JvmCompilationConfig

    /**
     * A set of additional to `.kt` and `.kts` Kotlin script extensions.
     *
     * Default value is an empty set.
     */
    public val kotlinScriptExtensions: Set<String>

    /**
     * @see [kotlinScriptExtensions]
     */
    public fun useKotlinScriptExtensions(kotlinScriptExtensions: Set<String>): JvmCompilationConfig

    /**
     * Provides a default [ClasspathSnapshotBasedIncrementalJvmCompilationConfig] allowing to use it as is or customizing for specific requirements.
     * Could be used as an overview to default values of the options (as they are implementation-specific).
     * @see [useIncrementalCompilation]
     */
    public fun generateClasspathSnapshotBasedIncrementalCompilationConfig(): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    /**
     * Configures usage of incremental compilation.
     * @param workingDirectory a working directory for incremental compilation internal state
     * @param sourcesChanges an instance of [SourcesChanges]
     * @param approachParameters an object representing mandatory parameters specific for the selected incremental compilation approach
     * @param options an object representing optional parameters and handles specific for the selected incremental compilation approach
     * @see [generateClasspathSnapshotBasedIncrementalCompilationConfig]]
     */
    public fun <P : IncrementalCompilationApproachParameters> useIncrementalCompilation(
        workingDirectory: File,
        sourcesChanges: SourcesChanges,
        approachParameters: P,
        options: IncrementalJvmCompilationConfig<P>,
    ) {
        error("This version of the Build Tools API does not support incremental compilation")
    }
}

/**
 * A configurator containing common handles and options to fine-tune incremental compilation.
 *
 * This interface is not intended to be implemented by API consumers.
 */
public interface IncrementalJvmCompilationConfig<P : IncrementalCompilationApproachParameters> {
    /**
     * A directory used as a base path for computing relative paths in the incremental compilation caches.
     *
     * If is not specified, incremental compilation caches will be non-relocatable
     *
     * Default values is `null`
     */
    public val projectDir: File?

    /**
     * @see [projectDir]
     */
    public fun useProjectDir(projectDir: File): IncrementalJvmCompilationConfig<P>

    /**
     * Controls whether incremental compilation should precisely analyze Java files for better changes detection
     *
     * Default value is defined by implementation of the API
     */
    public val usePreciseJavaTracking: Boolean

    /**
     * @see [usePreciseJavaTracking]
     */
    public fun usePreciseJavaTracking(value: Boolean): IncrementalJvmCompilationConfig<P>

    /**
     * Controls whether incremental compilation should perform file-by-file backup of files and revert them in the case of a failure
     *
     * Default value is defined by implementation of the API
     */
    public val usePreciseCompilationResultsBackup: Boolean

    /**
     * @see [usePreciseCompilationResultsBackup]
     */
    public fun usePreciseCompilationResultsBackup(value: Boolean): IncrementalJvmCompilationConfig<P>

    /**
     * Incremental compilation uses the PersistentHashMap of the intellij platform for storing caches.
     * This property controls whether the changes should remain in memory and not being flushed to the disk until we could mark the compilation as successful.
     *
     * Default value is defined by implementation of the API
     */
    public val keepIncrementalCompilationCachesInMemory: Boolean

    /**
     * @see [keepIncrementalCompilationCachesInMemory]
     */
    public fun keepIncrementalCompilationCachesInMemory(value: Boolean): IncrementalJvmCompilationConfig<P>

    /**
     * A handle to force non-incremental compilation, but using the mechanisms of incremental compilation.
     */
    public fun nonIncremental(): IncrementalJvmCompilationConfig<P>
}

/**
 * A configurator containing the handles and options to fine-tune classpath snapshots based approach for incremental compilation.
 *
 * This interface is not intended to be implemented by API consumers.
 */
public interface ClasspathSnapshotBasedIncrementalJvmCompilationConfig :
    IncrementalJvmCompilationConfig<ClasspathSnapshotBasedIncrementalCompilationApproachParameters> {
    /**
     * A handle to mark snapshot files as unchanged. Could be used if the check is already performed by an API consumer for the sake of optimization
     */
    public fun noClasspathSnapshotsChanges(): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun useProjectDir(projectDir: File): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun usePreciseJavaTracking(value: Boolean): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun usePreciseCompilationResultsBackup(value: Boolean): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun keepIncrementalCompilationCachesInMemory(value: Boolean): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun nonIncremental(): ClasspathSnapshotBasedIncrementalJvmCompilationConfig
}

/**
 * Mandatory parameters for an incremental compilation approach
 */
public sealed interface IncrementalCompilationApproachParameters

/**
 * Mandatory parameters of the classpath snapshots based incremental compilation approach
 */
public class ClasspathSnapshotBasedIncrementalCompilationApproachParameters(
    /**
     * The classpath snapshots files actual at the moment of compilation
     */
    public val newClasspathSnapshotFiles: List<File>,
    /**
     * The shrunk classpath snapshot, a result of the previous compilation. Could point to a non-existent file.
     * At the successful end of the compilation, the shrunk version of the [newClasspathSnapshotFiles] will be stored at this path.
     */
    public val shrunkClasspathSnapshot: File,
) : IncrementalCompilationApproachParameters