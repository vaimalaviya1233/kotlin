/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.api.jvm

import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.buildtools.api.SourcesChanges
import java.io.File

/**
 * A configuration interface of compilation options.
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

    public fun useLogger(logger: KotlinLogger): JvmCompilationConfig

    /**
     * A set of additional to `.kt` and `.kts` Kotlin script extensions.
     *
     * Default value is an empty set.
     */
    public val kotlinScriptExtensions: Set<String>

    public fun useKotlinScriptExtensions(kotlinScriptExtensions: Set<String>): JvmCompilationConfig

    public fun generateClasspathSnapshotBasedIncrementalCompilationConfig(): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

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
 * Options and handles to tune compilation
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

    public fun useProjectDir(projectDir: File): IncrementalJvmCompilationConfig<P>

    /**
     * Controls whether incremental compilation should precisely analyze Java files for better changes detection
     *
     * Default value is defined by implementation of the API
     */
    public val usePreciseJavaTracking: Boolean

    public fun usePreciseJavaTracking(value: Boolean): IncrementalJvmCompilationConfig<P>

    /**
     * Controls whether incremental compilation should perform file-by-file backup of files and revert them in the case of a failure
     *
     * Default value is defined by implementation of the API
     */
    public val usePreciseCompilationResultsBackup: Boolean

    public fun usePreciseCompilationResultsBackup(value: Boolean): IncrementalJvmCompilationConfig<P>

    /**
     * Incremental compilation uses the PersistentHashMap of the intellij platform for storing caches.
     * This property controls whether the changes should remain in memory and not being flushed to the disk until we could mark the compilation as successful.
     *
     * Default value is defined by implementation of the API
     */
    public val keepIncrementalCompilationCachesInMemory: Boolean

    public fun keepIncrementalCompilationCachesInMemory(value: Boolean): IncrementalJvmCompilationConfig<P>

    /**
     * Could be used to force the non-incremental mode
     */
    public fun nonIncremental(): IncrementalJvmCompilationConfig<P>
}

public interface ClasspathSnapshotBasedIncrementalJvmCompilationConfig :
    IncrementalJvmCompilationConfig<ClasspathSnapshotBasedIncrementalCompilationApproachParameters> {
    /**
     * Could be used to mark the snapshot files as not changed, if the check is already performed by a build system
     */
    public fun noClasspathSnapshotsChanges(): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun useProjectDir(projectDir: File): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun usePreciseJavaTracking(value: Boolean): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun usePreciseCompilationResultsBackup(value: Boolean): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun keepIncrementalCompilationCachesInMemory(value: Boolean): ClasspathSnapshotBasedIncrementalJvmCompilationConfig

    override fun nonIncremental(): ClasspathSnapshotBasedIncrementalJvmCompilationConfig
}

/**
 * Mandatory parameters for an IC approach
 */
public sealed interface IncrementalCompilationApproachParameters

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