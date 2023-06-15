/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.native

import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.DUMMY_FRAMEWORK_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_IMPORT_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_SPEC_TASK_NAME
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.util.assertProcessRunResult
import org.jetbrains.kotlin.gradle.util.replaceText
import org.jetbrains.kotlin.gradle.util.runProcess
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.*
import kotlin.test.*

@OsCondition(supportedOn = [OS.MAC], enabledOnCI = [OS.MAC])
@DisplayName("CocoaPods plugin tests")
@NativeGradlePluginTests
@GradleTestVersions(minVersion = TestVersions.Gradle.G_7_0)
@OptIn(EnvironmentalVariablesOverride::class)
class CocoaPodsIT : KGPBaseTest() {

    private val podfileImportPodPlaceholder = "#import_pod_directive"

    private val cocoapodsSingleKtPod = "native-cocoapods-single"
    private val cocoapodsMultipleKtPods = "native-cocoapods-multiple"
    private val cocoapodsTestsProjectName = "native-cocoapods-tests"
    private val cocoapodsCommonizationProjectName = "native-cocoapods-commonization"
    private val cocoapodsDependantPodsProjectName = "native-cocoapods-dependant-pods"

    private val dummyTaskName = ":$DUMMY_FRAMEWORK_TASK_NAME"
    private val podspecTaskName = ":$POD_SPEC_TASK_NAME"
    private val podImportTaskName = ":$POD_IMPORT_TASK_NAME"
    private val podInstallTaskName = ":${KotlinCocoapodsPlugin.POD_INSTALL_TASK_NAME}"

    private val defaultPodName = "AFNetworking"

    @BeforeAll
    fun setUp() {
        ensureCocoapodsInstalled()
    }

    @DisplayName("Pod import single")
    @GradleTest
    fun testPodImportSingle(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsSingleKtPod, gradleVersion) {

            buildWithCocoapodsWrapper(podImportTaskName) {
                podImportAsserts(buildGradleKts)
            }

            buildWithCocoapodsWrapper(":kotlin-library:podImport") {
                podImportAsserts(subProject("kotlin-library").buildGradleKts, "kotlin-library")
            }
        }
    }

    @DisplayName("Pod import multiple")
    @GradleTest
    fun testPodImportMultiple(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsMultipleKtPods, gradleVersion) {

            buildWithCocoapodsWrapper(podImportTaskName) {
                podImportAsserts(buildGradleKts)
            }

            buildWithCocoapodsWrapper(":kotlin-library:podImport") {
                podImportAsserts(subProject("kotlin-library").buildGradleKts, "kotlin-library")
            }

            buildWithCocoapodsWrapper(":second-library:podImport") {
                podImportAsserts(subProject("second-library").buildGradleKts, "second-library")
            }
        }
    }

    @DisplayName("Checking the warning about using deprecated podspec path")
    @GradleTest
    fun warnIfDeprecatedPodspecPathIsUsed(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsSingleKtPod, gradleVersion) {
            build(":kotlin-library:tasks") {
                assertOutputContains(
                    listOf("Deprecated DSL found on ${projectPath.toRealPath().absolutePathString()}", "kotlin-library", "build.gradle.kts")
                        .joinToString(separator = File.separator)
                )
            }
        }
    }

    @DisplayName("Build with error if project version is not specified for cocoapods")
    @GradleTest
    fun errorIfVersionIsNotSpecified(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            val filteredBuildScript = buildGradleKts.useLines { lines ->
                lines.filter { line -> "version = \"1.0\"" !in line }.joinToString(separator = "\n")
            }
            buildGradleKts.writeText(filteredBuildScript)

            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsGenerateWrapper = true
                )
            )

            buildAndFail(POD_IMPORT_TASK_NAME, buildOptions = buildOptions) {
                assertOutputContains("Cocoapods Integration requires pod version to be specified.")
            }
        }
    }

    @DisplayName("Dummy UTD")
    @GradleTest
    fun testDummyUTD(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {

            buildWithCocoapodsWrapper(dummyTaskName) {
                assertTasksExecuted(dummyTaskName)
            }
            buildWithCocoapodsWrapper(dummyTaskName) {
                assertTasksUpToDate(dummyTaskName)
            }
        }
    }

    @DisplayName("UTD after linking framework")
    @GradleTest
    fun testImportUTDAfterLinkingFramework(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            val linkTaskName = ":linkPodDebugFrameworkIOS"

            buildGradleKts.addCocoapodsBlock(
                """
                    framework {
                        baseName = "kotlin-library"
                    }
                    name = "kotlin-library"
                    podfile = project.file("ios-app/Podfile")
                """.trimIndent()
            )

            buildWithCocoapodsWrapper(podImportTaskName) {
                assertTasksExecuted(dummyTaskName)
                assertTasksExecuted(podInstallTaskName)
            }

            buildWithCocoapodsWrapper(linkTaskName) {
                assertTasksExecuted(linkTaskName)
            }

            buildWithCocoapodsWrapper(podImportTaskName) {
                assertTasksUpToDate(dummyTaskName)
                assertTasksUpToDate(podInstallTaskName)
            }
        }
    }

    @DisplayName("Changing framework type and checks UTD")
    @GradleTest
    fun testChangeFrameworkTypeUTD(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addCocoapodsBlock(
                """
                    framework {
                        baseName = "kotlin-library"
                    }
                    name = "kotlin-library"
                    podfile = project.file("ios-app/Podfile")
                """.trimIndent()
            )

            buildWithCocoapodsWrapper(podImportTaskName) {
                assertTasksExecuted(dummyTaskName)
                assertTasksExecuted(podInstallTaskName)
            }

            buildWithCocoapodsWrapper(podImportTaskName) {
                assertTasksUpToDate(dummyTaskName)
                assertTasksUpToDate(podInstallTaskName)
            }

            buildGradleKts.addFrameworkBlock("isStatic = true")
            buildWithCocoapodsWrapper(podImportTaskName) {
                assertTasksExecuted(dummyTaskName)
                assertTasksExecuted(podInstallTaskName)
            }

            buildWithCocoapodsWrapper(podImportTaskName) {
                assertTasksUpToDate(dummyTaskName)
                assertTasksUpToDate(podInstallTaskName)
            }

        }
    }

    @DisplayName("UTD podspec")
    @GradleTest
    fun testUTDPodspec(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {

            buildWithCocoapodsWrapper(podspecTaskName)

            buildGradleKts.addCocoapodsBlock("license = \"new license name\"")
            buildWithCocoapodsWrapper(podspecTaskName) {
                assertTasksExecuted(podspecTaskName)
            }

            buildGradleKts.addCocoapodsBlock("license = \"new license name\"")
            buildWithCocoapodsWrapper(podspecTaskName) {
                assertTasksUpToDate(podspecTaskName)
            }
        }
    }

    @DisplayName("UTD with podspec deployment target")
    @GradleTest
    fun testUTDPodspecDeploymentTarget(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {

            buildWithCocoapodsWrapper(podspecTaskName)

            buildGradleKts.addCocoapodsBlock("ios.deploymentTarget = \"12.5\"")
            buildWithCocoapodsWrapper(podspecTaskName) {
                assertTasksExecuted(podspecTaskName)
            }

            buildWithCocoapodsWrapper(podspecTaskName) {
                assertTasksUpToDate(podspecTaskName)
            }
        }
    }

    @DisplayName("Installing pod without pod file")
    @GradleTest
    fun testPodInstallWithoutPodFile(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildWithCocoapodsWrapper(podInstallTaskName)
        }
    }

    @DisplayName("Pods with dependencies support")
    @GradleTest
    fun supportPodsWithDependencies(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addPod("AlamofireImage")

            buildWithCocoapodsWrapper(podImportTaskName) {
                podImportAsserts(buildGradleKts)
            }
        }
    }

    @DisplayName("Custom package name")
    @GradleTest
    fun testCustomPackageName(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {

            buildGradleKts.addPod("AFNetworking", "packageName = \"AFNetworking\"")
            val srcFileForChanging = projectPath.resolve("src/iosMain/kotlin/A.kt")
            srcFileForChanging.replaceText(
                "println(\"hi!\")", "println(AFNetworking.AFNetworkingReachabilityNotificationStatusItem)"
            )
            buildWithCocoapodsWrapper("assemble")
        }
    }

    @DisplayName("Cinterop extra opts")
    @GradleTest
    fun testCinteropExtraOpts(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addPod("AFNetworking", "extraOpts = listOf(\"-help\")")
            buildWithCocoapodsWrapper("cinteropAFNetworkingIOS") {
                assertOutputContains("Usage: cinterop options_list")
            }
        }
    }

    @DisplayName("Cocoapods with regular framework definition")
    @GradleTest
    fun testCocoapodsWithRegularFrameworkDefinition(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addKotlinBlock("iosX64(\"iOS\") {binaries.framework{}}")
            buildWithCocoapodsWrapper(podImportTaskName)
        }
    }

    @DisplayName("Checking sync framework")
    @GradleTest
    fun testSyncFramework(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {

            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsPlatform = "iphonesimulator",
                    cocoapodsArchs = "x86_64",
                    cocoapodsConfiguration = "Debug"
                )
            )
            build("syncFramework", buildOptions = buildOptions) {
                assertTasksExecuted(":linkPodDebugFrameworkIOS")
                assertFileInProjectExists("build/cocoapods/framework/cocoapods.framework/cocoapods")
            }
        }
    }

    @DisplayName("Sync framework with custom Xcode configuration")
    @GradleTest
    fun testSyncFrameworkCustomXcodeConfiguration(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addCocoapodsBlock("xcodeConfigurationToNativeBuildType[\"CUSTOM\"] = org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG\n")
            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsPlatform = "iphonesimulator",
                    cocoapodsArchs = "x86_64",
                    cocoapodsConfiguration = "CUSTOM"
                )
            )
            build("syncFramework", buildOptions = buildOptions) {
                assertTasksExecuted(":linkPodDebugFrameworkIOS")
                assertFileInProjectExists(("build/cocoapods/framework/cocoapods.framework/cocoapods"))
            }
        }
    }

    @DisplayName("Checking sync framework with invalid platform")
    @GradleTest
    fun testSyncFrameworkInvalidArch(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {

            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsPlatform = "iphoneos",
                    cocoapodsArchs = "x86_64",
                    cocoapodsConfiguration = "Debug"
                )
            )
            buildAndFail("syncFramework", buildOptions = buildOptions) {
                assertOutputContains("Architecture x86_64 is not supported for platform iphoneos")
            }
        }
    }

    @DisplayName("Checking sync framework with multiple platforms")
    @GradleTest
    fun testSyncFrameworkMultiplePlatforms(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {

            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsPlatform = "iphoneos iphonesimulator",
                    cocoapodsArchs = "arm64",
                    cocoapodsConfiguration = "Debug"
                )
            )
            buildAndFail("syncFramework", buildOptions = buildOptions) {
                assertOutputContains("kotlin.native.cocoapods.platform has to contain a single value only.")
            }
        }
    }

    @DisplayName("Sync framework multiple achitectures with custom name")
    @GradleTest
    fun testSyncFrameworkMultipleArchitecturesWithCustomName(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            val frameworkName = "customSdk"
            buildGradleKts.appendText(
                """
                    |
                    |kotlin {
                    |    iosSimulatorArm64()
                    |    cocoapods {
                    |       framework {
                    |           baseName = "$frameworkName"
                    |       }
                    |    }
                    |}
                """.trimMargin()
            )
            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsPlatform = "iphonesimulator",
                    cocoapodsArchs = "arm64 x86_64",
                    cocoapodsConfiguration = "Debug",
                    cocoapodsGenerateWrapper = true
                )
            )

            build("syncFramework", buildOptions = buildOptions) {
                // Check that an output framework is a dynamic framework
                val framework = projectPath.resolve("build/cocoapods/framework/$frameworkName.framework/$frameworkName")
                assertProcessRunResult(runProcess(listOf("file", framework.absolutePathString()), projectPath.toFile())) {
                    assertOutputContains("universal binary with 2 architectures")
                    assertOutputContains("(for architecture x86_64)")
                    assertOutputContains("(for architecture arm64)")
                }
            }
        }
    }

    @DisplayName("Xcode style errors when sync framework configuration failed")
    @GradleTest
    fun testSyncFrameworkUseXcodeStyleErrorsWhenConfigurationFailed(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.appendText(
                """
                kotlin {
                    sourceSets["commonMain"].dependencies {
                        implementation("com.example.unknown:dependency:0.0.1")
                    }       
                }
                """.trimIndent()
            )
            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsPlatform = "iphonesimulator",
                    cocoapodsArchs = "x86_64",
                    cocoapodsConfiguration = "Debug"
                )
            )
            buildAndFail("syncFramework", buildOptions = buildOptions) {
                assertOutputContains("error: Could not find com.example.unknown:dependency:0.0.1.")
            }
        }
    }

    @DisplayName("Xcode style errors when sync framework compilation failed")
    @GradleTest
    fun testSyncFrameworkUseXcodeStyleErrorsWhenCompilationFailed(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            projectPath.resolve("src/commonMain/kotlin/A.kt").appendText("this can't be compiled")
            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsPlatform = "iphonesimulator",
                    cocoapodsArchs = "x86_64",
                    cocoapodsConfiguration = "Debug"
                )
            )
            buildAndFail("syncFramework", buildOptions = buildOptions) {
                assertOutputContains("/native-cocoapods-template/src/commonMain/kotlin/A.kt:5:2: error: Expecting a top level declaration")
                assertOutputContains("error: Compilation finished with errors")
            }
        }
    }

    @DisplayName("Other tasks use gradle style errors when compilation failed")
    @GradleTest
    fun testOtherTasksUseGradleStyleErrorsWhenCompilationFailed(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            projectPath.resolve("src/commonMain/kotlin/A.kt").appendText("this can't be compiled")
            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsPlatform = "iphonesimulator",
                    cocoapodsArchs = "x86_64",
                    cocoapodsConfiguration = "Debug"
                )
            )
            buildAndFail("linkPodDebugFrameworkIOS", buildOptions = buildOptions) {
                assertOutputContains("e: file:///")
                assertOutputContains("/native-cocoapods-template/src/commonMain/kotlin/A.kt:5:2 Expecting a top level declaration")
                assertOutputDoesNotContain("error: Compilation finished with errors")
            }
        }
    }

    @DisplayName("Other tasks use Xcode style errors when compilation failed and `useXcodeMessageStyle` option enabled")
    @GradleTest
    fun testOtherTasksUseXcodeStyleErrorsWhenCompilationFailedAndOptionEnabled(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            projectPath.resolve("src/commonMain/kotlin/A.kt").appendText("this can't be compiled")
            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsPlatform = "iphonesimulator",
                    cocoapodsArchs = "x86_64",
                    cocoapodsConfiguration = "Debug",
                    useXcodeMessageStyle = true
                )
            )
            buildAndFail("linkPodDebugFrameworkIOS", buildOptions = buildOptions) {
                assertOutputContains("/native-cocoapods-template/src/commonMain/kotlin/A.kt:5:2: error: Expecting a top level declaration")
                assertOutputContains("error: Compilation finished with errors")
            }
        }
    }

    @DisplayName("Pod dependency in unit tests")
    @GradleTest
    fun testPodDependencyInUnitTests(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsTestsProjectName, gradleVersion) {
            buildWithCocoapodsWrapper(":iosX64Test")
        }
    }

    @DisplayName("Cinterop commonization off")
    @GradleTest
    fun testCinteropCommonizationOff(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsCommonizationProjectName, gradleVersion) {
            buildWithCocoapodsWrapper(":commonize") {
                assertTasksExecuted(":commonizeNativeDistribution")
                assertTasksNotExecuted(":cinteropAFNetworkingIosArm64")
                assertTasksNotExecuted(":cinteropAFNetworkingIosX64")
                assertTasksNotExecuted(":commonizeCInterop")
            }
        }
    }

    @DisplayName("Cinterop commonization on")
    @GradleTest
    fun testCinteropCommonizationOn(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsCommonizationProjectName, gradleVersion) {
            buildWithCocoapodsWrapper(":commonize", "-Pkotlin.mpp.enableCInteropCommonization=true") {
                assertTasksExecuted(":commonizeNativeDistribution")
                assertTasksExecuted(":cinteropAFNetworkingIosArm64")
                assertTasksExecuted(":cinteropAFNetworkingIosX64")
                assertTasksExecuted(":commonizeCInterop")
            }
        }
    }

    @DisplayName("Checks pod publishing")
    @GradleTest
    fun testPodPublishing(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addKotlinBlock("iosX64(\"iOS\") {binaries.framework{}}")
            buildWithCocoapodsWrapper(":podPublishXCFramework") {
                assertTasksExecuted(":podPublishReleaseXCFramework")
                assertTasksExecuted(":podPublishDebugXCFramework")
                assertDirectoryInProjectExists("build/cocoapods/publish/release/cocoapods.xcframework")
                assertDirectoryInProjectExists("build/cocoapods/publish/debug/cocoapods.xcframework")
                assertFileInProjectExists("build/cocoapods/publish/release/cocoapods.podspec")
                assertFileInProjectExists("build/cocoapods/publish/debug/cocoapods.podspec")
                val actualPodspecContentWithoutBlankLines =
                    projectPath.resolve("build/cocoapods/publish/release/cocoapods.podspec").readText()
                        .lineSequence()
                        .filter { it.isNotBlank() }
                        .joinToString("\n")

                assertEquals(publishPodspecContent, actualPodspecContentWithoutBlankLines)
            }
        }
    }

    @DisplayName("Checks pod publishing with custom properties")
    @GradleTest
    fun testPodPublishingWithCustomProperties(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addCocoapodsBlock("name = \"CustomPod\"")
            buildGradleKts.addCocoapodsBlock("version = \"2.0\"")
            buildGradleKts.addCocoapodsBlock("publishDir = projectDir.resolve(\"CustomPublishDir\")")
            buildGradleKts.addCocoapodsBlock("license = \"'MIT'\"")
            buildGradleKts.addCocoapodsBlock("authors = \"{ 'Kotlin Dev' => 'kotlin.dev@jetbrains.com' }\"")
            buildGradleKts.addCocoapodsBlock("extraSpecAttributes[\"social_media_url\"] = \"'https://twitter.com/kotlin'\"")
            buildGradleKts.addCocoapodsBlock("extraSpecAttributes[\"vendored_frameworks\"] = \"'CustomFramework.xcframework'\"")
            buildGradleKts.addCocoapodsBlock("extraSpecAttributes[\"libraries\"] = \"'xml'\"")
            buildGradleKts.addPod(defaultPodName)

            buildWithCocoapodsWrapper(":podPublishXCFramework") {
                assertTasksExecuted(":podPublishReleaseXCFramework")
                assertTasksExecuted(":podPublishDebugXCFramework")
                assertDirectoryInProjectExists("CustomPublishDir/release/cocoapods.xcframework")
                assertDirectoryInProjectExists("CustomPublishDir/debug/cocoapods.xcframework")
                assertFileInProjectExists("CustomPublishDir/release/CustomPod.podspec")
                assertFileInProjectExists("CustomPublishDir/debug/CustomPod.podspec")
                val actualPodspecContentWithoutBlankLines = projectPath.resolve("CustomPublishDir/release/CustomPod.podspec").readText()
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .joinToString("\n")

                assertEquals(publishPodspecCustomContent, actualPodspecContentWithoutBlankLines)
            }
        }
    }

    @DisplayName("Checks pod install UTD")
    @GradleTest
    fun testPodInstallUpToDateCheck(gradleVersion: GradleVersion) {
        val subProjectName = "kotlin-library"
        val subprojectPodImportTask = ":$subProjectName$podImportTaskName"
        val subprojectPodspecTask = ":$subProjectName$podspecTaskName"
        val subprojectPodInstallTask = ":$subProjectName$podInstallTaskName"
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsSingleKtPod, gradleVersion) {
            buildGradleKts.addCocoapodsBlock("ios.deploymentTarget = \"14.0\"")
            buildWithCocoapodsWrapper(subprojectPodImportTask) {
                assertTasksExecuted(listOf(subprojectPodspecTask, subprojectPodInstallTask))
            }

            subProject(subProjectName).buildGradleKts.addPod(defaultPodName)
            buildWithCocoapodsWrapper(subprojectPodImportTask) {
                assertTasksExecuted(listOf(subprojectPodspecTask, subprojectPodInstallTask))
            }

            buildWithCocoapodsWrapper(subprojectPodImportTask) {
                assertTasksUpToDate(subprojectPodspecTask, subprojectPodInstallTask)
            }

            addPodToPodfile("ios-app", defaultPodName)
            buildWithCocoapodsWrapper(subprojectPodImportTask) {
                assertTasksUpToDate(subprojectPodspecTask)
                assertTasksExecuted(listOf(subprojectPodInstallTask))
            }
        }
    }

    @DisplayName("Cinterop Klibs provide linker opts to framework")
    @GradleTest
    fun testCinteropKlibsProvideLinkerOptsToFramework(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addPod("AFNetworking")
            buildWithCocoapodsWrapper("cinteropAFNetworkingIOS") {
                val cinteropKlib = projectPath.resolve("build/classes/kotlin/iOS/main/cinterop/cocoapods-cinterop-AFNetworking.klib")
                val manifestLines = ZipFile(cinteropKlib.toFile()).use { zip ->
                    zip.getInputStream(zip.getEntry("default/manifest")).bufferedReader().use { it.readLines() }
                }

                assertContains(manifestLines, "linkerOpts=-framework AFNetworking")
            }
        }
    }

    @DisplayName("Link only pods")
    @GradleTest
    fun testLinkOnlyPods(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addCocoapodsBlock(
                """
                    pod("AFNetworking") { linkOnly = true }
                    pod("SSZipArchive", linkOnly = true)
                    pod("SDWebImage/Core")
                """.trimIndent()
            )

            buildAndAssertAllTasks(
                notRegisteredTasks = listOf(":cinteropAFNetworkingIOS", ":cinteropSSZipArchiveIOS"),
                buildOptions = this.buildOptions.copy(
                    nativeOptions = this.buildOptions.nativeOptions.copy(
                        cocoapodsGenerateWrapper = true
                    )
                )
            )

            buildWithCocoapodsWrapper(":linkPodDebugFrameworkIOS") {
                assertTasksExecuted(":podBuildAFNetworkingIphonesimulator")
                assertTasksExecuted(":podBuildSDWebImageIphonesimulator")
                assertTasksExecuted(":podBuildSSZipArchiveIphonesimulator")

                assertTasksExecuted(":cinteropSDWebImageIOS")

                // TODO(Dmitrii Krasnov): rewrite it, when GeneralNativeIT will be migrated to new test dsl
                assertOutputContains(
                    """
                    |	-linker-option
                    |	-framework
                    |	-linker-option
                    |	AFNetworking
                    """.trimMargin()
                )

                assertOutputContains(
                    """
                    |	-linker-option
                    |	-framework
                    |	-linker-option
                    |	SSZipArchive
                    """.trimMargin()
                )
            }
        }
    }

    @DisplayName("Usage link only with static framework produces message")
    @GradleTest
    fun testUsageLinkOnlyWithStaticFrameworkProducesMessage(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(gradleVersion = gradleVersion) {
            buildGradleKts.addCocoapodsBlock(
                """
                    framework {
                        isStatic = true
                    }
        
                    pod("AFNetworking") { linkOnly = true }
                """.trimIndent()
            )
            buildWithCocoapodsWrapper(":linkPodDebugFrameworkIOS") {
                assertOutputContains("Dependency on 'AFNetworking' with option 'linkOnly=true' is unused for building static frameworks")
            }
        }
    }

    @DisplayName("Hierarchy of dependant pods compiles successfully")
    @GradleTest
    fun testHierarchyOfDependantPodsCompilesSuccessfully(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsDependantPodsProjectName, gradleVersion) {
            buildWithCocoapodsWrapper(":compileKotlinIosX64")
        }
    }

    @DisplayName("Configuration fails when trying to depend on non-declared pod")
    @GradleTest
    fun testConfigurationFailsWhenTryingToDependOnNonDeclaredPod(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsDependantPodsProjectName, gradleVersion) {
            buildGradleKts.addCocoapodsBlock(
                """
                    pod("Foo") { useInteropBindingFrom("JBNonExistent") }
                """.trimIndent()
            )

            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsGenerateWrapper = true
                )
            )

            buildAndFail(":help", buildOptions = buildOptions) {
                assertOutputContains("Couldn't find declaration of pod 'JBNonExistent' (interop-binding dependency of pod 'Foo')")
            }
        }
    }

    @DisplayName("Configuration fails when dependant pods are in the wrong order")
    @GradleTest
    fun testConfigurationFailsWhenDependantPodsAreInTheWrongOrder(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsDependantPodsProjectName, gradleVersion) {
            buildGradleKts.addCocoapodsBlock(
                """
                    pod("Foo") { useInteropBindingFrom("Bar") }
                    pod("Bar")
                """.trimIndent()
            )

            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsGenerateWrapper = true
                )
            )

            buildAndFail(":help", buildOptions = buildOptions) {
                assertOutputContains("Couldn't find declaration of pod 'Bar' (interop-binding dependency of pod 'Foo')")
            }
        }
    }

    @DisplayName("Configuration fails when pod depends on itself")
    @GradleTest
    fun testConfigurationFailsWhenPodDependsOnItself(gradleVersion: GradleVersion) {
        nativeProjectWithCocoapodsAndIosAppPodFile(cocoapodsDependantPodsProjectName, gradleVersion) {
            buildGradleKts.addCocoapodsBlock(
                """
                    pod("Foo") { useInteropBindingFrom("Foo") }
                """.trimIndent()
            )

            val buildOptions = this.buildOptions.copy(
                nativeOptions = this.buildOptions.nativeOptions.copy(
                    cocoapodsGenerateWrapper = true
                )
            )

            buildAndFail(":help", buildOptions = buildOptions) {
                assertOutputContains("Pod 'Foo' has an interop-binding dependency on itself")
            }
        }
    }

    @DisplayName("Configuration cache works in a complex scenario")
    @GradleTestVersions(minVersion = TestVersions.Gradle.G_7_6, maxVersion = TestVersions.Gradle.G_8_1)
    @GradleTest
    fun testConfigurationCacheWorksInAComplexScenario(gradleVersion: GradleVersion) {
        val buildOptions = defaultBuildOptions.copy(
            nativeOptions = defaultBuildOptions.nativeOptions.copy(
                cocoapodsGenerateWrapper = true,
                cocoapodsPlatform = "iphonesimulator",
                cocoapodsArchs = "x86_64",
                cocoapodsConfiguration = "Debug"
            ),
            configurationCache = true
        )
        nativeProjectWithCocoapodsAndIosAppPodFile(
            gradleVersion = gradleVersion,
            buildOptions = buildOptions
        ) {
            buildGradleKts.addCocoapodsBlock("""pod("Base64", version = "1.1.2")""")

            val tasks = arrayOf(
                ":podspec",
                ":podImport",
                ":podPublishDebugXCFramework",
                ":podPublishReleaseXCFramework",
                ":syncFramework",
            )

            val executableTasks = listOf(
                ":podspec",
                ":podPublishDebugXCFramework",
                ":podPublishReleaseXCFramework",
                ":linkPodDebugFrameworkIOS",
            )

            build(*tasks) {
                assertTasksExecuted(executableTasks)

                assertOutputContains("Calculating task graph as no configuration cache is available for tasks")

                assertOutputContains("Configuration cache entry stored.")
            }

            build("clean")

            build(*tasks) {
                assertOutputContains("Reusing configuration cache.")
            }

            build(*tasks) {
                assertTasksUpToDate(executableTasks)
            }
        }
    }

    private fun TestProject.buildWithCocoapodsWrapper(
        vararg buildArguments: String,
        assertions: BuildResult.() -> Unit = {},
    ) {
        val buildOptions = this.buildOptions.copy(
            nativeOptions = this.buildOptions.nativeOptions.copy(
                cocoapodsGenerateWrapper = true
            )
        )
        build(*buildArguments, buildOptions = buildOptions) {
            assertions()
        }
    }

    private fun TestProject.addPodToPodfile(iosAppLocation: String, pod: String) {
        projectPath
            .resolve(iosAppLocation)
            .resolve("Podfile")
            .replaceText(podfileImportPodPlaceholder, "pod '$pod'")
    }

    private val publishPodspecContent =
        """
            Pod::Spec.new do |spec|
                spec.name                     = 'cocoapods'
                spec.version                  = '1.0'
                spec.homepage                 = 'https://github.com/JetBrains/kotlin'
                spec.source                   = { :http=> ''}
                spec.authors                  = ''
                spec.license                  = ''
                spec.summary                  = 'CocoaPods test library'
                spec.vendored_frameworks      = 'cocoapods.xcframework'
                spec.libraries                = 'c++'
                spec.ios.deployment_target = '13.5'
            end
        """.trimIndent()

    private val publishPodspecCustomContent =
        """
            Pod::Spec.new do |spec|
                spec.name                     = 'CustomPod'
                spec.version                  = '2.0'
                spec.homepage                 = 'https://github.com/JetBrains/kotlin'
                spec.source                   = { :http=> ''}
                spec.authors                  = { 'Kotlin Dev' => 'kotlin.dev@jetbrains.com' }
                spec.license                  = 'MIT'
                spec.summary                  = 'CocoaPods test library'
                spec.ios.deployment_target = '13.5'
                spec.dependency 'AFNetworking'
                spec.social_media_url = 'https://twitter.com/kotlin'
                spec.vendored_frameworks = 'CustomFramework.xcframework'
                spec.libraries = 'xml'
            end
        """.trimIndent()
}