/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNodeJsExtension
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmProject.Companion.PACKAGE_JSON
import org.jetbrains.kotlin.gradle.utils.property
import java.io.File

abstract class PublicPackageJsonTask : DefaultTask() {

    // Only in configuration phase
    // Not part of configuration caching

    @Transient
    private val nodeJs = project.rootProject.kotlinNodeJsExtension

//    private val rootResolver: KotlinRootNpmResolver
//        get() = nodeJs.resolver

//    private val compilationResolver: KotlinCompilationNpmResolver
//        get() = rootResolver[projectPath][compilationDisambiguatedName.get()]

    // -----

    @get:Internal
    internal abstract val npmResolutionManager: Property<KotlinNpmResolutionManager>

    abstract val compilationDisambiguatedName: Property<String>

    private val projectPath = project.path

    private val projectVersion = project.version.toString()

    abstract val jsIrCompilation: Property<Boolean>

    abstract val npmProjectName: Property<String>

    abstract val npmProjectMain: Property<String>

    private val packageJsonHandlers: List<PackageJson.() -> Unit>
        get() = npmResolutionManager.get().parameters.packageJsonHandlers.get().getValue("$projectPath:$compilationDisambiguatedName")


    @get:Input
    val packageJsonCustomFields: Map<String, Any?>
        get() = PackageJson(fakePackageJsonValue, fakePackageJsonValue)
            .apply {
                packageJsonHandlers.forEach { it() }
            }.customFields

//    private val compilationResolver
//        get() = npmResolutionManager.get().resolution.get()[projectPath][compilationName]

//    private val confCompResolver
//        get() = nodeJs.let {
//            it.resolver[projectPath][compilationName]
//        }

//    @get:Internal
//    internal val packageJsonProducer: KotlinCompilationNpmResolver.PackageJsonProducer by lazy {
//        confCompResolver.packageJsonProducer
//        /*.also { it.compilationResolver = this }*/
//    }

    private val compilationResolution
        get() = npmResolutionManager.get().resolution.get()[projectPath][compilationDisambiguatedName.get()]
            .getResolutionOrResolve(
                npmResolutionManager.get()
            )

    @get:Input
    val externalDependencies: Collection<NpmDependencyDeclaration>
        get() = compilationResolution.externalNpmDependencies

    private val defaultPackageJsonFile by lazy {
        project.buildDir
            .resolve("tmp")
            .resolve(name)
            .resolve(PACKAGE_JSON)
    }

    @get:OutputFile
    var packageJsonFile: File by property { defaultPackageJsonFile }

    @TaskAction
    fun resolve() {
        packageJson(npmProjectName.get(), projectVersion, npmProjectMain.get(), externalDependencies, packageJsonHandlers).let { packageJson ->
            packageJson.main = "${npmProjectName}.js"

            if (jsIrCompilation.get()) {
                packageJson.types = "${npmProjectName}.d.ts"
            }

            packageJson.apply {
                listOf(
                    dependencies,
                    devDependencies,
                    peerDependencies,
                    optionalDependencies
                ).forEach { it.processDependencies() }
            }

            packageJson.saveTo(this@PublicPackageJsonTask.packageJsonFile)
        }
    }

    private fun MutableMap<String, String>.processDependencies() {
        filter { (_, version) ->
            version.isFileVersion()
        }.forEach { (key, _) ->
            remove(key)
        }
    }

    companion object {
        const val NAME = "publicPackageJson"
    }
}