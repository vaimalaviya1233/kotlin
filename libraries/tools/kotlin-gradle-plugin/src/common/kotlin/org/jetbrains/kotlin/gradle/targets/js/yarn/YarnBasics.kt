/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.yarn

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.internal.service.ServiceRegistry
import org.jetbrains.kotlin.gradle.internal.execWithProgress
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmApi
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency.Scope.PEER
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmEnvironment
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinCompilationNpmResolution
import java.io.File

abstract class YarnBasics : NpmApi {

    fun yarnExec(
        services: ServiceRegistry,
        logger: Logger,
        nodeJs: NpmEnvironment,
        yarn: YarnEnv,
        dir: File,
        description: String,
        args: List<String>
    ) {
        services.execWithProgress(description) { exec ->
            val arguments = args
                .plus(
                    if (logger.isDebugEnabled) "--verbose" else ""
                )
                .plus(
                    if (yarn.ignoreScripts) "--ignore-scripts" else ""
                ).filter { it.isNotEmpty() }

            val nodeExecutable = nodeJs.nodeExecutable
            if (!yarn.ignoreScripts) {
                val nodePath = if (nodeJs.isWindows) {
                    File(nodeExecutable).parent
                } else {
                    nodeExecutable
                }
                exec.environment(
                    "PATH",
                    "$nodePath${File.pathSeparator}${System.getenv("PATH")}"
                )
            }

            val command = yarn.executable
            if (yarn.standalone) {
                exec.executable = command
                exec.args = arguments
            } else {
                exec.executable = nodeExecutable
                exec.args = listOf(command) + arguments
            }

            exec.workingDir = dir
        }

    }

    protected fun yarnLockReadTransitiveDependencies(
        nodeWorkDir: File,
        srcDependenciesList: Collection<NpmDependency>
    ) {
        val yarnLock = nodeWorkDir
            .resolve("yarn.lock")
            .takeIf { it.isFile }
            ?: return

        val entryRegistry = YarnEntryRegistry(yarnLock)
        val visited = mutableMapOf<NpmDependency, NpmDependency>()

        fun resolveRecursively(src: NpmDependency) {
            if (src.scope == PEER) {
                return
            }

            val copy = visited[src]
            if (copy != null) {
                src.resolvedVersion = copy.resolvedVersion
                src.integrity = copy.integrity
                src.dependencies.addAll(copy.dependencies)
                return
            }
            visited[src] = src

            val deps = entryRegistry.find(src.name, src.version)

            src.resolvedVersion = deps.version
            src.integrity = deps.integrity

            deps.dependencies.mapTo(src.dependencies) { dep ->
                val scopedName = dep.scopedName
                val child = NpmDependency(
                    objectFactory = src.objectFactory,
                    name = scopedName.toString(),
                    version = dep.version ?: "*"
                )
                child.parent = src

                resolveRecursively(child)

                child
            }
        }

        srcDependenciesList.forEach { src ->
            resolveRecursively(src)
        }
    }
}
