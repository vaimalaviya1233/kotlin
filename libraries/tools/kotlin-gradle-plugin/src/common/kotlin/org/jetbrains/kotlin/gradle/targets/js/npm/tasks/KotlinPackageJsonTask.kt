/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.plugin.categoryByName
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.plugin.mpp.disambiguateName
import org.jetbrains.kotlin.gradle.plugin.sources.KotlinDependencyScope
import org.jetbrains.kotlin.gradle.plugin.sources.compilationDependencyConfigurationByScope
import org.jetbrains.kotlin.gradle.plugin.sources.sourceSetDependencyConfigurationByScope
import org.jetbrains.kotlin.gradle.plugin.usesPlatformOf
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNodeJsExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNpmResolutionManager
import org.jetbrains.kotlin.gradle.targets.js.npm.*
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.KotlinCompilationNpmResolver
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.KotlinRootNpmResolver
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.PACKAGE_JSON_UMBRELLA_TASK_NAME
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.PackageJsonProducerInputs
import org.jetbrains.kotlin.gradle.tasks.registerTask
import java.io.File

abstract class KotlinPackageJsonTask : DefaultTask() {
    // Only in configuration phase
    // Not part of configuration caching

    private val nodeJs: NodeJsRootExtension
        get() = project.rootProject.kotlinNodeJsExtension

    private val rootResolver: KotlinRootNpmResolver
        get() = nodeJs.resolver

    private val compilationResolver: KotlinCompilationNpmResolver
        get() = rootResolver[projectPath][compilationDisambiguatedName.get()]

//    private fun findDependentTasks(): Collection<Any> =
//        compilationResolver.compilationNpmResolution.internalDependencies.map { dependency ->
//            nodeJs.resolver[dependency.projectPath][dependency.compilationName].npmProject.packageJsonTaskPath
//        } + compilationResolver.compilationNpmResolution.internalCompositeDependencies.map { dependency ->
//            dependency.includedBuild?.task(":$PACKAGE_JSON_UMBRELLA_TASK_NAME") ?: error("includedBuild instance is not available")
//            dependency.includedBuild.task(":${RootPackageJsonTask.NAME}")
//        }

    // -----

    @get:Internal
    internal abstract val npmResolutionManager: Property<KotlinNpmResolutionManager>

    @get:Internal
    internal abstract val gradleNodeModules: Property<GradleNodeModulesCache>

    @get:Internal
    internal abstract val compositeNodeModules: Property<CompositeNodeModulesCache>

    @get:Input
    internal abstract val components: Property<ResolvedComponentResult>

    @get:Input
    internal abstract val map: MapProperty<ComponentArtifactIdentifier, File>

    private val projectPath = project.path

    @get:Internal
    abstract val compilationDisambiguatedName: Property<String>

    private val packageJsonHandlers: List<PackageJson.() -> Unit>
        get() = npmResolutionManager.get().parameters.packageJsonHandlers.get()
            .getValue("$projectPath:${compilationDisambiguatedName.get()}")

    @get:Input
    val packageJsonCustomFields: Map<String, Any?> by lazy {
        PackageJson(fakePackageJsonValue, fakePackageJsonValue)
            .apply {
                packageJsonHandlers.forEach { it() }
            }.customFields
    }


    @get:Input
    internal val toolsNpmDependencies: List<String> by lazy {
        nodeJs.taskRequirements
            .getCompilationNpmRequirements(projectPath, compilationDisambiguatedName.get())
            .map { it.toString() }
            .sorted()
    }

    // nested inputs are processed in configuration phase
    // so npmResolutionManager must not be used
//    @get:Nested
//    internal val producerInputs: PackageJsonProducerInputs by lazy {
//        compilationResolver.compilationNpmResolution.inputs
//    }

    @get:OutputFile
    abstract val packageJson: Property<File>

    @TaskAction
    fun resolve() {
        val resolvedConfiguration = components.get() to map.get().map { (key, value) -> key.componentIdentifier to value }.toMap()
        npmResolutionManager.get().resolution.get()[projectPath][compilationDisambiguatedName.get()]
            .resolve(
                npmResolutionManager = npmResolutionManager.get(),
                logger = logger,
                resolvedConfiguration = resolvedConfiguration
            )
    }

    companion object {
        fun create(compilation: KotlinJsCompilation): TaskProvider<KotlinPackageJsonTask> {
            val target = compilation.target
            val project = target.project
            val npmProject = compilation.npmProject
            val nodeJsTaskProviders = project.rootProject.kotlinNodeJsExtension

            val rootClean = project.rootProject.tasks.named(BasePlugin.CLEAN_TASK_NAME)
            val npmCachesSetupTask = nodeJsTaskProviders.npmCachesSetupTaskProvider
            val packageJsonTaskName = npmProject.packageJsonTaskName
            val packageJsonUmbrella = nodeJsTaskProviders.packageJsonUmbrellaTaskProvider

            fun createAggregatedConfiguration(): Pair<Provider<ResolvedComponentResult>, Provider<Map<ComponentArtifactIdentifier, File>>> {
                val all = project.configurations.create(compilation.disambiguateName("npm"))

                all.usesPlatformOf(target)
                all.attributes.attribute(Usage.USAGE_ATTRIBUTE, KotlinUsages.consumerRuntimeUsage(target))
                all.attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.categoryByName(Category.LIBRARY))
                all.isVisible = false
                all.isCanBeConsumed = false
                all.isCanBeResolved = true
                all.description = "NPM configuration for $compilation."

                KotlinDependencyScope.values().forEach { scope ->
                    val compilationConfiguration = project.compilationDependencyConfigurationByScope(
                        compilation,
                        scope
                    )
                    all.extendsFrom(compilationConfiguration)
                    compilation.allKotlinSourceSets.forEach { sourceSet ->
                        val sourceSetConfiguration = project.configurations.sourceSetDependencyConfigurationByScope(sourceSet, scope)
                        all.extendsFrom(sourceSetConfiguration)
                    }
                }

                // We don't have `kotlin-js-test-runner` in NPM yet
                all.dependencies.add(nodeJsTaskProviders.versions.kotlinJsTestRunner.createDependency(project))

                return all.incoming.resolutionResult.rootComponent to all.incoming.artifacts.resolvedArtifacts.map {
                    it.map { it.id to it.file }.toMap()
                }
            }

            val packageJsonTask = project.registerTask<KotlinPackageJsonTask>(packageJsonTaskName) { task ->
                task.compilationDisambiguatedName.set(compilation.disambiguatedName)
                task.description = "Create package.json file for $compilation"
                task.group = NodeJsRootPlugin.TASKS_GROUP_NAME

                task.npmResolutionManager.apply {
                    val service = project.rootProject.kotlinNpmResolutionManager
                    set(service)
                    disallowChanges()
                    task.usesService(service)
                }

                val createAggregatedConfiguration = createAggregatedConfiguration()
                task.components.set(createAggregatedConfiguration.first)
                task.map.set(createAggregatedConfiguration.second)
//                task.resolvedConfiguration = createAggregatedConfiguration

                task.gradleNodeModules.apply {
                    val service =
                        project.gradle.sharedServices.registerIfAbsent("gradle-node-modules", GradleNodeModulesCache::class.java) {
                            error("must be already registered")
                        }
                    set(service)
                    disallowChanges()
                    task.usesService(service)
                }

                task.compositeNodeModules.apply {
                    val service =
                        project.gradle.sharedServices.registerIfAbsent("composite-node-modules", CompositeNodeModulesCache::class.java) {
                            error("must be already registered")
                        }
                    set(service)
                    disallowChanges()
                    task.usesService(service)
                }

                task.packageJson.set(compilation.npmProject.packageJsonFile)

                task.onlyIf {
                    it as KotlinPackageJsonTask
                    it.npmResolutionManager.get().isConfiguringState()
                }

//                task.dependsOn(target.project.provider { task.findDependentTasks() })
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