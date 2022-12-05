/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.resolver

import org.jetbrains.kotlin.gradle.targets.js.npm.*
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmProject.Companion.PACKAGE_JSON
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinCompilationNpmResolution
import java.io.Serializable
import java.io.File

internal class PackageJsonProducer(
    var internalDependencies: Collection<InternalDependency>,
    var internalCompositeDependencies: Collection<CompositeDependency>,
    var externalGradleDependencies: Collection<FileExternalGradleDependency>,
    var externalNpmDependencies: Collection<NpmDependencyDeclaration>,
    var fileCollectionDependencies: Collection<FileCollectionExternalGradleDependency>,
    val projectPath: String,
    val projectPackagesDir: File,
    val rootDir: File,
    val compilationDisambiguatedName: String,
    val npmProjectName: String,
    val npmProjectVersion: String,
    val npmProjectMain: String,
    val npmProjectPackageJsonFile: File,
    val npmProjectDir: File
) : Serializable {

    val inputs: PackageJsonProducerInputs
        get() = PackageJsonProducerInputs(
            internalDependencies.map { it.projectName },
            internalCompositeDependencies.flatMap { it.getPackages() },
            externalGradleDependencies.map { it.file },
            externalNpmDependencies.map { it.uniqueRepresentation() },
            fileCollectionDependencies.flatMap { it.files }
        )

    private var closed = false
    private var resolution: KotlinCompilationNpmResolution? = null

    @Synchronized
    fun resolve(
        skipWriting: Boolean = false,
        npmResolutionManager: KotlinNpmResolutionManager
    ): KotlinCompilationNpmResolution {
        check(!closed) { "$this already closed" }
        check(resolution == null) { "$this already resolved" }

        return createPackageJson(
            skipWriting,
            npmResolutionManager
        ).also {
            resolution = it
        }
    }

    @Synchronized
    fun getResolutionOrResolve(
        npmResolutionManager: KotlinNpmResolutionManager,
    ): KotlinCompilationNpmResolution {

        return resolution ?: resolve(
            skipWriting = true,
            npmResolutionManager
        )
    }

    @Synchronized
    fun close(): KotlinCompilationNpmResolution {
        check(!closed) { "$this already closed" }
        val resolution = resolution!! /* getResolutionOrResolve(npmResolutionManager) */
        closed = true
        return resolution
    }

    fun createPackageJson(
        skipWriting: Boolean,
        npmResolutionManager: KotlinNpmResolutionManager
    ): KotlinCompilationNpmResolution {
        val rootResolver = npmResolutionManager.parameters.resolver.get()

        internalDependencies.map {
            val packageJsonProducer: PackageJsonProducer = rootResolver[it.projectPath][it.compilationName]
            packageJsonProducer.getResolutionOrResolve(
                npmResolutionManager,
            )
        }
        val importedExternalGradleDependencies = externalGradleDependencies.mapNotNull {
            npmResolutionManager.parameters.gradleNodeModulesProvider.get().get(it.dependencyName, it.dependencyVersion, it.file)
        } + fileCollectionDependencies.flatMap { dependency ->
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
        }.filterNotNull()
        val transitiveNpmDependencies = importedExternalGradleDependencies.flatMap {
            it.dependencies
        }.filter { it.scope != NpmDependency.Scope.DEV }

        val compositeDependencies = internalCompositeDependencies.flatMap { dependency ->
            dependency.getPackages()
                .map { file ->
                    npmResolutionManager.parameters.compositeNodeModulesProvider.get().get(
                        dependency.dependencyName,
                        dependency.dependencyVersion,
                        file
                    )
                }
        }.filterNotNull()

        val toolsNpmDependencies = rootResolver.tasksRequirements
            .getCompilationNpmRequirements(projectPath, compilationDisambiguatedName)

        val otherNpmDependencies = toolsNpmDependencies + transitiveNpmDependencies
        val allNpmDependencies = disambiguateDependencies(externalNpmDependencies, otherNpmDependencies)
        val packageJsonHandlers =
            npmResolutionManager.parameters.packageJsonHandlers.get()["$projectPath:${compilationDisambiguatedName}"]
                ?: emptyList() /*if (compilationResolver.compilation != null) {
                compilationResolver.compilation.packageJsonHandlers
            } else {
                compilationResolver.rootResolver.getPackageJsonHandlers(projectPath, compilationResolver.compilationDisambiguatedName)
            }*/

        val packageJson = packageJson(
            npmProjectName,
            npmProjectVersion,
            npmProjectMain,
            allNpmDependencies,
            packageJsonHandlers
        )

        compositeDependencies.forEach {
            packageJson.dependencies[it.name] = it.version
        }

        packageJsonHandlers.forEach {
            it(packageJson)
        }

        if (!skipWriting) {
            packageJson.saveTo(npmProjectPackageJsonFile)
        }

        return KotlinCompilationNpmResolution(
            npmProjectDir,
            compositeDependencies,
            importedExternalGradleDependencies,
            allNpmDependencies,
            packageJson
        )
    }

    private fun disambiguateDependencies(
        direct: Collection<NpmDependencyDeclaration>,
        others: Collection<NpmDependencyDeclaration>,
    ): Collection<NpmDependencyDeclaration> {
        val unique = others.groupBy(NpmDependencyDeclaration::name)
            .filterKeys { k -> direct.none { it.name == k } }
            .mapNotNull { (name, dependencies) ->
                dependencies.maxByOrNull { dep ->
                    SemVer.from(dep.version, true)
                }?.also { selected ->
                    if (dependencies.size > 1) {
                        compilationResolver.project.logger.warn(
                            """
                                Transitive npm dependency version clash for compilation "${compilationResolver.compilation.name}"
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