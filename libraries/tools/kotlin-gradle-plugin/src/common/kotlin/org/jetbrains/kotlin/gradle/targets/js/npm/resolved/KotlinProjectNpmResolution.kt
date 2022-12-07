/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.resolved

import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.PackageJsonProducer
import java.io.Serializable

/**
 * Info about NPM projects inside particular gradle [project].
 */
internal class KotlinProjectNpmResolution(
    val project: String,
    val byCompilation: Map<String, PackageJsonProducer>,
) : Serializable {
    val npmProjects: Collection<PackageJsonProducer>
        get() = byCompilation.values

    operator fun get(compilationName: String): PackageJsonProducer {
        return byCompilation.getValue(compilationName)
    }

    companion object {
        fun empty(project: String) = KotlinProjectNpmResolution(project, emptyMap())
    }
}