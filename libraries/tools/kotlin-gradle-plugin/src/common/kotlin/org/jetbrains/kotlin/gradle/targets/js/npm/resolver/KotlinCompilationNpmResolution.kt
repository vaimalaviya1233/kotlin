/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.resolver

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.targets.js.nodejs.TasksRequirements
import org.jetbrains.kotlin.gradle.targets.js.npm.*
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmProject.Companion.PACKAGE_JSON
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.PreparedKotlinCompilationNpmResolution
import java.io.Serializable
import java.io.File

class KotlinCompilationNpmResolution(
//    var internalDependencies: Collection<InternalDependency>,
//    var internalCompositeDependencies: Collection<CompositeDependency>,
//    var externalGradleDependencies: Collection<FileExternalGradleDependency>,
//    var externalNpmDependencies: Collection<NpmDependencyDeclaration>,
//    var fileCollectionDependencies: Collection<FileCollectionExternalGradleDependency>,
    val projectPath: String,
    val projectPackagesDir: File,
    val rootDir: File,
    val compilationDisambiguatedName: String,
    val npmProjectName: String,
    val npmProjectVersion: String,
    val npmProjectMain: String,
    val npmProjectPackageJsonFile: File,
    val npmProjectDir: File,
    val tasksRequirements: TasksRequirements
) : Serializable {

//    val inputs: PackageJsonProducerInputs
//        get() = PackageJsonProducerInputs(
//            internalDependencies.map { it.projectName },
//            internalCompositeDependencies.flatMap { it.getPackages() },
//            externalGradleDependencies.map { it.file },
//            externalNpmDependencies.map { it.uniqueRepresentation() },
//            fileCollectionDependencies.flatMap { it.files }
//        )

    private var closed = false
    private var resolution: PreparedKotlinCompilationNpmResolution? = null

    @Synchronized
    fun resolve(
        skipWriting: Boolean = false,
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfiguration: Pair<ResolvedComponentResult, Map<ComponentIdentifier, File>>
    ): PreparedKotlinCompilationNpmResolution {
        check(resolution == null) { "$this already resolved" }

        return createPackageJson(
            skipWriting,
            npmResolutionManager,
            logger,
            resolvedConfiguration
        ).also {
            resolution = it
        }
    }

    @Synchronized
    fun getResolutionOrResolve(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfiguration: Pair<ResolvedComponentResult, Map<ComponentIdentifier, File>>? = null
    ): PreparedKotlinCompilationNpmResolution {

        return resolution ?: resolve(
            skipWriting = true,
            npmResolutionManager,
            logger,
            resolvedConfiguration!!
        )
    }

    @Synchronized
    fun close(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
    ): PreparedKotlinCompilationNpmResolution {
        check(!closed) { "$this already closed" }
        closed = true
        return getResolutionOrResolve(npmResolutionManager, logger)
    }

    fun createPackageJson(
        skipWriting: Boolean,
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfiguration: Pair<ResolvedComponentResult, Map<ComponentIdentifier, File>>
    ): PreparedKotlinCompilationNpmResolution {
        val rootResolver = npmResolutionManager.parameters.resolution.get()

        val visitor = ConfigurationVisitor()
        visitor.visit(resolvedConfiguration.first to resolvedConfiguration.second)

//        internalDependencies.map {
//            val compilationNpmResolution: KotlinCompilationNpmResolution = rootResolver[it.projectPath][it.compilationName]
//            compilationNpmResolution.getResolutionOrResolve(
//                npmResolutionManager,
//                logger
//            )
//        }
        val importedExternalGradleDependencies = visitor.externalGradleDependencies.mapNotNull {
            npmResolutionManager.parameters.gradleNodeModulesProvider.get().get(it.component.module, it.component.version, it.artifact)
        } /*+ fileCollectionDependencies.flatMap { dependency ->
            dependency.files
                // Gradle can hash with FileHasher only files and only existed files
                .filter { it.isFile }
                .map { file ->
                    npmResolutionManager.parameters.gradleNodeModulesProvider.get().get(
                        file.name,
                        dependency.dependencyVersion ?: "0.0.1",
                        file
                    )
                }
        }.filterNotNull()*/
        val transitiveNpmDependencies = importedExternalGradleDependencies.flatMap {
            it.dependencies
        }.filter { it.scope != NpmDependency.Scope.DEV }

        val compositeDependencies = emptyList<GradleNodeModule>()

//        val compositeDependencies = internalCompositeDependencies.flatMap { dependency ->
//            dependency.getPackages()
//                .map { file ->
//                    npmResolutionManager.parameters.compositeNodeModulesProvider.get().get(
//                        dependency.dependencyName,
//                        dependency.dependencyVersion,
//                        file
//                    )
//                }
//        }.filterNotNull()

        val toolsNpmDependencies = tasksRequirements
            .getCompilationNpmRequirements(projectPath, compilationDisambiguatedName)

        val otherNpmDependencies = toolsNpmDependencies + transitiveNpmDependencies
        val allNpmDependencies = disambiguateDependencies(visitor.externalNpmDependencies, otherNpmDependencies, logger)
        val packageJsonHandlers =
            npmResolutionManager.parameters.packageJsonHandlers.get()["$projectPath:${compilationDisambiguatedName}"]
                ?: emptyList()

        val packageJson = packageJson(
            npmProjectName,
            npmProjectVersion,
            npmProjectMain,
            allNpmDependencies,
            packageJsonHandlers
        )

//        compositeDependencies.forEach {
//            packageJson.dependencies[it.name] = it.version
//        }

        packageJsonHandlers.forEach {
            it(packageJson)
        }

        if (!skipWriting) {
            packageJson.saveTo(npmProjectPackageJsonFile)
        }

        return PreparedKotlinCompilationNpmResolution(
            npmProjectDir,
            compositeDependencies,
            importedExternalGradleDependencies,
            allNpmDependencies,
        )
    }

    private fun disambiguateDependencies(
        direct: Collection<NpmDependencyDeclaration>,
        others: Collection<NpmDependencyDeclaration>,
        logger: Logger,
    ): Collection<NpmDependencyDeclaration> {
        val unique = others.groupBy(NpmDependencyDeclaration::name)
            .filterKeys { k -> direct.none { it.name == k } }
            .mapNotNull { (name, dependencies) ->
                dependencies.maxByOrNull { dep ->
                    SemVer.from(dep.version, true)
                }?.also { selected ->
                    if (dependencies.size > 1) {
                        logger.warn(
                            """
                                Transitive npm dependency version clash for compilation "${compilationDisambiguatedName}"
                                    Candidates:
                                ${dependencies.joinToString("\n") { "\t\t" + it.name + "@" + it.version }}
                                    Selected:
                                        ${selected.name}@${selected.version}
                                """.trimIndent()
                        )
                    }
                }
            }
        return direct + unique
    }

    internal fun CompositeDependency.getPackages(): List<File> {
        val packages = includedBuildDir.resolve(projectPackagesDir.relativeTo(rootDir))
        return packages
            .list()
            ?.map { packages.resolve(it) }
            ?.map { it.resolve(PACKAGE_JSON) }
            ?: emptyList()
    }
}

class ConfigurationVisitor {
    val internalDependencies = mutableSetOf<InternalDependency>()
    val internalCompositeDependencies = mutableSetOf<CompositeDependency>()
    val externalGradleDependencies = mutableSetOf<ExternalGradleDependency>()
    val externalNpmDependencies = mutableSetOf<NpmDependencyDeclaration>()
    val fileCollectionDependencies = mutableSetOf<FileCollectionExternalGradleDependency>()

    private val visitedDependencies = mutableSetOf<ComponentIdentifier>()

    fun visit(configuration: Pair<ResolvedComponentResult, Map<ComponentIdentifier, File>>) {
        configuration.first.dependencies.forEach { result ->
            if (result is ResolvedDependencyResult) {
                val owner = result.resolvedVariant.externalVariant.orElse(result.resolvedVariant).owner
                visitDependency(owner, configuration.second.getValue(owner))
            } else {
                println("WTF ${result}")
            }
        }
//            configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach {
//                visitDependency(it)
//            }
//
//            configuration.allDependencies.forEach { dependency ->
//                when (dependency) {
//                    is NpmDependency -> externalNpmDependencies.add(dependency)
//                    is FileCollectionDependency -> fileCollectionDependencies.add(
//                        FileCollectionExternalGradleDependency(
//                            dependency.files.files,
//                            dependency.version
//                        )
//                    )
//                }
//            }

//        TODO: rewrite when we get general way to have inter compilation dependencies
//        if (compilation.name == KotlinCompilation.TEST_COMPILATION_NAME) {
//            val main = compilation.target.compilations.findByName(KotlinCompilation.MAIN_COMPILATION_NAME) as KotlinJsCompilation
//            internalDependencies.add(
//                InternalDependency(
//                    projectResolver.project.path,
//                    main.disambiguatedName,
//                    projectResolver[main].npmProject.name
//                )
//            )
//        }

        val hasPublicNpmDependencies = externalNpmDependencies.isNotEmpty()

//        if (compilation.isMain() && hasPublicNpmDependencies) {
//            project.tasks
//                .withType(Zip::class.java)
//                .named(npmProject.target.artifactsTaskName)
//                .configure { task ->
//                    task.from(publicPackageJsonTaskHolder)
//                }
//        }
    }

    private fun visitDependency(dependency: ComponentIdentifier, second: File) {
        if (dependency in visitedDependencies) return
        visitedDependencies.add(dependency)
        visitArtifact(dependency, second)
//            visitArtifacts(dependency, dependency.)

//            dependency.children.forEach {
//                visitDependency(it)
//            }
    }

//        private fun visitArtifacts(
//            dependency: ResolvedDependency,
//            artifacts: MutableSet<ResolvedArtifact>
//        ) {
//            artifacts.forEach { visitArtifact(dependency, it) }
//        }

    private fun visitArtifact(
        dependency: ComponentIdentifier,
        artifact: File
    ) {
//            val artifactId = artifact.id
//        val componentIdentifier = dependency.id

//            if (artifactId `is` CompositeProjectComponentArtifactMetadata) {
//                visitCompositeProjectDependency(dependency, componentIdentifier as ProjectComponentIdentifier)
//                return
//            }

//        if (componentIdentifier is ProjectComponentIdentifier) {
//            visitProjectDependency(componentIdentifier)
//            return
//        }

        if (dependency is ModuleComponentIdentifier)

        externalGradleDependencies.add(ExternalGradleDependency(dependency, artifact))
    }

//    private fun visitCompositeProjectDependency(
//        dependency: ResolvedDependency,
//        componentIdentifier: ProjectComponentIdentifier
//    ) {
//        check(target is KotlinJsIrTarget) {
//            """
//                Composite builds for Kotlin/JS are supported only for IR compiler.
//                Use kotlin.js.compiler=ir in gradle.properties or
//                js(IR) {
//                ...
//                }
//                """.trimIndent()
//        }
//
//        (componentIdentifier as DefaultProjectComponentIdentifier).let { identifier ->
//            val includedBuild = project.gradle.includedBuild(identifier.identityPath.topRealPath().name!!)
//            internalCompositeDependencies.add(
//                CompositeDependency(dependency.moduleName, dependency.moduleVersion, includedBuild.projectDir, includedBuild)
//            )
//        }
//    }

//    private fun visitProjectDependency(
//        componentIdentifier: ProjectComponentIdentifier
//    ) {
//        val dependentProject = project.findProject(componentIdentifier.projectPath)
//            ?: error("Cannot find project ${componentIdentifier.projectPath}")
//
//        rootResolver.findDependentResolver(project, dependentProject)
//            ?.forEach { dependentResolver ->
//                internalDependencies.add(
//                    InternalDependency(
//                        dependentResolver.projectPath,
//                        dependentResolver.compilationDisambiguatedName,
//                        dependentResolver.npmProject.name
//                    )
//                )
//            }
//    }

//    fun toPackageJsonProducer() = PackageJsonProducer(
//        internalDependencies,
//        internalCompositeDependencies,
//        externalGradleDependencies.map {
//            it.component to it.artifact
//        },
//        externalNpmDependencies.map { it.toDeclaration() },
//        fileCollectionDependencies,
//        projectPath
//    )
}