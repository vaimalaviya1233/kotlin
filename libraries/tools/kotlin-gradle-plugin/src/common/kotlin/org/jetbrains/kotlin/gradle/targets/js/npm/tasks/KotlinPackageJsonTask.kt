/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNodeJsExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNodeJsTaskProvidersExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNpmResolutionManager
import org.jetbrains.kotlin.gradle.targets.js.npm.*
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.KotlinCompilationNpmResolver
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.KotlinRootNpmResolver
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.PACKAGE_JSON_UMBRELLA_TASK_NAME
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.PackageJsonProducerInputs
import org.jetbrains.kotlin.gradle.tasks.registerTask
import java.io.File

abstract class KotlinPackageJsonTask : DefaultTask() {

    init {
        onlyIf {
            npmResolutionManager.get().isConfiguringState()
        }
    }

    // Only in configuration phase
    // Not part of configuration caching

    @Transient
    private val nodeJs: Property<NodeJsRootExtension> = project.objects.property(NodeJsRootExtension::class.java)

    private val rootResolver: KotlinRootNpmResolver
        get() = nodeJs.get().resolver

    private val compilationResolver: KotlinCompilationNpmResolver
        get() = rootResolver[projectPath][compilationDisambiguatedName.get()]

    private fun findDependentTasks(): Collection<Any> =
        compilationResolver.packageJsonProducer.internalDependencies.map { dependency ->
            nodeJs.get().resolver[dependency.projectPath][dependency.compilationName].npmProject.packageJsonTaskPath
        } + compilationResolver.packageJsonProducer.internalCompositeDependencies.map { dependency ->
            dependency.includedBuild?.task(":$PACKAGE_JSON_UMBRELLA_TASK_NAME") ?: error("includedBuild instance is not available")
            dependency.includedBuild.task(":${RootPackageJsonTask.NAME}")
        }

    // -----

    @get:Internal
    internal abstract val npmResolutionManager: Property<KotlinNpmResolutionManager>

    @get:Internal
    internal abstract val gradleNodeModules: Property<GradleNodeModulesCache>

    @get:Internal
    internal abstract val compositeNodeModules: Property<CompositeNodeModulesCache>

    private val projectPath = project.path

    abstract val compilationDisambiguatedName: Property<String>

    private val packageJsonHandlers: List<PackageJson.() -> Unit>
        get() = npmResolutionManager.get().parameters.packageJsonHandlers.get().getValue("$projectPath:$compilationDisambiguatedName")

    @get:Input
    val packageJsonCustomFields: Map<String, Any?> by lazy {
        PackageJson(fakePackageJsonValue, fakePackageJsonValue)
            .apply {
                packageJsonHandlers.forEach { it() }
            }.customFields
    }


    @get:Input
    internal val toolsNpmDependencies: List<String> by lazy {
        nodeJs.get().taskRequirements
            .getCompilationNpmRequirements(projectPath, compilationDisambiguatedName.get())
            .map { it.toString() }
            .sorted()
    }

    // nested inputs are processed in configuration phase
    // so npmResolutionManager must not be used
    @get:Nested
    internal val producerInputs: PackageJsonProducerInputs by lazy {
        compilationResolver.packageJsonProducer.inputs
    }

    @get:OutputFile
    abstract val packageJson: Property<File>

    @TaskAction
    fun resolve() {
        npmResolutionManager.get().resolver.get()[projectPath][compilationDisambiguatedName.get()]
            .resolve(
                npmResolutionManager = npmResolutionManager.get(),
            )
    }

    companion object {
        fun create(compilation: KotlinJsCompilation): TaskProvider<KotlinPackageJsonTask> {
            val target = compilation.target
            val project = target.project
            val npmProject = compilation.npmProject
            val nodeJs = project.rootProject.kotlinNodeJsExtension
            val nodeJsTaskProviders = project.rootProject.kotlinNodeJsTaskProvidersExtension

            val rootClean = project.rootProject.tasks.named(BasePlugin.CLEAN_TASK_NAME)
            val npmCachesSetupTask = nodeJsTaskProviders.npmCachesSetupTaskProvider
            val packageJsonTaskName = npmProject.packageJsonTaskName
            val packageJsonUmbrella = nodeJsTaskProviders.packageJsonUmbrellaTaskProvider
            val packageJsonTask = project.registerTask<KotlinPackageJsonTask>(packageJsonTaskName) { task ->
                task.nodeJs.set(nodeJs)
                task.compilationDisambiguatedName.set(compilation.disambiguatedName)
                task.description = "Create package.json file for $compilation"
                task.group = NodeJsRootPlugin.TASKS_GROUP_NAME

                task.npmResolutionManager.apply {
                    set(project.rootProject.kotlinNpmResolutionManager)
                    disallowChanges()
                    task.usesService(this)
                }

                task.gradleNodeModules.apply {
                    set(project.gradle.sharedServices.registerIfAbsent("gradle-node-modules", GradleNodeModulesCache::class.java) {
                        error("must be already registered")
                    })
                    disallowChanges()
                    task.usesService(this)
                }

                task.compositeNodeModules.apply {
                    set(project.gradle.sharedServices.registerIfAbsent("composite-node-modules", CompositeNodeModulesCache::class.java) {
                        error("must be already registered")
                    })
                    disallowChanges()
                    task.usesService(this)
                }

                task.packageJson.set(compilation.npmProject.packageJsonFile)

                task.dependsOn(target.project.provider { task.findDependentTasks() })
                task.dependsOn(npmCachesSetupTask)
                task.mustRunAfter(rootClean)
            }

            packageJsonUmbrella.configure { task ->
                task.inputs.file(packageJsonTask.map { it.packageJson })
            }

            nodeJsTaskProviders.rootPackageJsonTaskProvider.configure { it.mustRunAfter(packageJsonTask) }

            return packageJsonTask
        }
    }
}