/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.yarn

import org.gradle.api.logging.Logger
import org.gradle.internal.service.ServiceRegistry
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnv
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmApi
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmEnvironment
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinCompilationNpmResolution
import java.io.File

class Yarn : NpmApi {
    private val yarnWorkspaces = YarnWorkspaces()

    private fun getDelegate(): NpmApi =
        yarnWorkspaces

    override fun preparedFiles(nodeJs: NodeJsEnv): Collection<File> =
        yarnWorkspaces.preparedFiles(nodeJs)

    override fun prepareRootProject(
        nodeJs: NodeJsEnv,
        rootProjectName: String,
        rootProjectVersion: String,
        logger: Logger,
        subProjects: Collection<KotlinCompilationNpmResolution>,
        resolutions: Map<String, String>,
    ) = yarnWorkspaces
        .prepareRootProject(
            nodeJs,
            rootProjectName,
            rootProjectVersion,
            logger,
            subProjects,
            resolutions,
        )

    override fun resolveRootProject(
        services: ServiceRegistry,
        logger: Logger,
        nodeJs: NodeJsEnv,
        yarn: YarnEnv,
        npmProjects: Collection<KotlinCompilationNpmResolution>,
        cliArgs: List<String>
    ) {
        yarnWorkspaces
            .resolveRootProject(
                services,
                logger,
                nodeJs,
                yarn,
                npmProjects,
                cliArgs
            )
    }
}