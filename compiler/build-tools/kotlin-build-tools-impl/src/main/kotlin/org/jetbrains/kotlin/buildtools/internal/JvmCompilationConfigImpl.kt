/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.internal

import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.buildtools.api.SourcesChanges
import org.jetbrains.kotlin.buildtools.api.jvm.*
import java.io.File

internal class JvmCompilationConfigImpl(
    override var kotlinScriptExtensions: Set<String> = emptySet(),
    override var logger: KotlinLogger = DefaultKotlinLogger,
) : JvmCompilationConfig {
    override fun useLogger(logger: KotlinLogger): JvmCompilationConfig {
        this.logger = logger
        return this
    }

    override fun useKotlinScriptExtensions(kotlinScriptExtensions: Set<String>): JvmCompilationConfig {
        this.kotlinScriptExtensions = kotlinScriptExtensions
        return this
    }

    override fun generateClasspathSnapshotBasedIncrementalCompilationConfig() = ClasspathSnapshotBasedIncrementalJvmCompilationConfigImpl()

    override fun <P : IncrementalCompilationApproachParameters> useIncrementalCompilation(
        workingDirectory: File,
        sourcesChanges: SourcesChanges,
        approachParameters: P,
        options: IncrementalJvmCompilationConfig<P>,
    ) = TODO("Incremental compilation is not yet supported to run via the Build Tools API")
}

internal abstract class JvmIncrementalCompilationConfigImpl<P : IncrementalCompilationApproachParameters>(
    override var usePreciseJavaTracking: Boolean = true,
    override var usePreciseCompilationResultsBackup: Boolean = false,
    override var keepIncrementalCompilationCachesInMemory: Boolean = false,
    override var projectDir: File? = null,
) : IncrementalJvmCompilationConfig<P> {
    override fun useProjectDir(projectDir: File): IncrementalJvmCompilationConfig<P> {
        this.projectDir = projectDir
        return this
    }

    override fun usePreciseJavaTracking(value: Boolean): IncrementalJvmCompilationConfig<P> {
        usePreciseJavaTracking = value
        return this
    }

    override fun usePreciseCompilationResultsBackup(value: Boolean): IncrementalJvmCompilationConfig<P> {
        usePreciseCompilationResultsBackup = value
        return this
    }

    override fun keepIncrementalCompilationCachesInMemory(value: Boolean): IncrementalJvmCompilationConfig<P> {
        keepIncrementalCompilationCachesInMemory = value
        return this
    }

    override fun nonIncremental(): IncrementalJvmCompilationConfig<P> {
        return this
    }
}

internal class ClasspathSnapshotBasedIncrementalJvmCompilationConfigImpl :
    JvmIncrementalCompilationConfigImpl<ClasspathSnapshotBasedIncrementalCompilationApproachParameters>(),
    ClasspathSnapshotBasedIncrementalJvmCompilationConfig {
    override fun useProjectDir(projectDir: File): ClasspathSnapshotBasedIncrementalJvmCompilationConfig {
        super.useProjectDir(projectDir)
        return this
    }

    override fun usePreciseJavaTracking(value: Boolean): ClasspathSnapshotBasedIncrementalJvmCompilationConfig {
        super.usePreciseJavaTracking(value)
        return this
    }

    override fun usePreciseCompilationResultsBackup(value: Boolean): ClasspathSnapshotBasedIncrementalJvmCompilationConfig {
        super.usePreciseCompilationResultsBackup(value)
        return this
    }

    override fun keepIncrementalCompilationCachesInMemory(value: Boolean): ClasspathSnapshotBasedIncrementalJvmCompilationConfig {
        super.keepIncrementalCompilationCachesInMemory(value)
        return this
    }

    override fun nonIncremental(): ClasspathSnapshotBasedIncrementalJvmCompilationConfig {
        super.nonIncremental()
        return this
    }

    override fun noClasspathSnapshotsChanges(): ClasspathSnapshotBasedIncrementalJvmCompilationConfig {
        return this
    }
}