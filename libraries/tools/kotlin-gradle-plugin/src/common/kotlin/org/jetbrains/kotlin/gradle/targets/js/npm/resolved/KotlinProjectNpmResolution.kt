/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.resolved

import org.jetbrains.kotlin.gradle.targets.js.RequiredKotlinJsDependency
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.PackageJsonProducer

/**
 * Info about NPM projects inside particular gradle [project].
 */
internal class KotlinProjectNpmResolution(
    val project: String,
    val npmProjects: List<PackageJsonProducer>,
    val taskRequirements: Map<String, Collection<RequiredKotlinJsDependency>>
) {
    val byCompilation: Map<String, PackageJsonProducer> by lazy { npmProjects.associateBy { it.compilationDisambiguatedName } }

    operator fun get(compilationName: String): PackageJsonProducer {
//        check(compilation.target.project.path == project)
        return byCompilation.getValue(compilationName)
    }

    companion object {
        fun empty(project: String) = KotlinProjectNpmResolution(project, listOf(), mapOf())
    }
}