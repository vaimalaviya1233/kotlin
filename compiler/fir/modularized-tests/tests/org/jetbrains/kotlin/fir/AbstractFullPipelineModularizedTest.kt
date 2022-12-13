/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.build.JvmSourceRoot
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.modules.KotlinModuleXmlBuilder
import org.jetbrains.kotlin.util.PerformanceCounter
import java.io.FileOutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

private val USE_BUILD_FILE: Boolean = System.getProperty("fir.bench.use.build.file", "true").toBooleanLenient()!!
private val JVM_TARGET: String = System.getProperty("fir.bench.jvm.target", "1.8")

abstract class AbstractFullPipelineModularizedTest : AbstractModularizedTest() {

    private val asyncProfilerControl = AsyncProfilerControl()
    protected abstract val useK2: Boolean

    data class ModuleStatus(val data: ModuleData, val targetInfo: String) {
        var compilationError: String? = null
        var jvmInternalError: String? = null
        var exceptionMessage: String = "NO MESSAGE"
    }

    private val totalModules = mutableListOf<ModuleStatus>()
    private val okModules = mutableListOf<ModuleStatus>()
    private val errorModules = mutableListOf<ModuleStatus>()
    private val crashedModules = mutableListOf<ModuleStatus>()

    protected lateinit var totalPassResult: MutableMap<String, CumulativeTime>

    override fun beforePass(pass: Int) {
        totalPassResult = mutableMapOf()
        totalModules.clear()
        okModules.clear()
        errorModules.clear()
        crashedModules.clear()

        asyncProfilerControl.beforePass(pass, reportDateStr)
    }

    override fun afterPass(pass: Int) {
        asyncProfilerControl.afterPass(pass, reportDateStr)

        createReport(finalReport = pass == PASSES - 1)
        require(totalModules.isNotEmpty()) { "No modules were analyzed" }
        require(okModules.isNotEmpty()) { "All of $totalModules is failed" }
    }

    private fun formatReportTable(stream: PrintStream, title: String, time: CumulativeTime) {
        println(title);

        /*var totalGcTimeMs = 0L
        var totalGcCount = 0L
        printTable(stream) {
            row("Name", "Time", "Count")
            separator()
            fun gcRow(name: String, timeMs: Long, count: Long) {
                row {
                    cell(name, align = LEFT)
                    timeCell(timeMs, inputUnit = TableTimeUnit.MS)
                    cell(count.toString())
                }
            }
            for (measurement in time.gcInfo.values) {
                totalGcTimeMs += measurement.gcTime
                totalGcCount += measurement.collections
                gcRow(measurement.name, measurement.gcTime, measurement.collections)
            }
            separator()
            gcRow("Total", totalGcTimeMs, totalGcCount)
        }*/

        printTable(stream) {
            row("Phase", "Time", "Files", "L/S")
            separator()

            fun phase(name: String, timeMs: Long, files: Int, lines: Int) {
                row {
                    cell(name, align = LEFT)
                    timeCell(timeMs, inputUnit = TableTimeUnit.MS)
                    cell(files.toString())
                    linePerSecondCell(lines, timeMs, timeUnit = TableTimeUnit.MS)
                }
            }
            for (component in time.components) {
                phase(component.key, component.value, time.files, time.lines)
            }

            separator()
            phase("Total", time.totalTime(), time.files, time.lines)
        }

    }

    private fun configureBaseArguments(args: K2JVMCompilerArguments, moduleData: ModuleData, tmp: Path) {
        args.reportPerf = true
        args.jvmTarget = JVM_TARGET
        args.allowKotlinPackage = true
        if (USE_BUILD_FILE) {
            configureArgsUsingBuildFile(args, moduleData, tmp)
        } else {
            configureRegularArgs(args, moduleData, tmp)
        }
    }

    private fun configureRegularArgs(args: K2JVMCompilerArguments, moduleData: ModuleData, tmp: Path) {
        args.classpath = moduleData.classpath.joinToString(separator = ":") { it.absolutePath }
        args.javaSourceRoots = moduleData.javaSourceRoots.map { it.path.absolutePath }.toTypedArray()
        args.freeArgs = moduleData.sources.map { it.absolutePath }
        args.destination = tmp.toAbsolutePath().toFile().toString()
        args.friendPaths = moduleData.friendDirs.map { it.canonicalPath }.toTypedArray()
        args.optIn = moduleData.optInAnnotations.toTypedArray()
    }

    private fun configureArgsUsingBuildFile(args: K2JVMCompilerArguments, moduleData: ModuleData, tmp: Path) {
        val builder = KotlinModuleXmlBuilder()
        builder.addModule(
            moduleData.name,
            tmp.toAbsolutePath().toFile().toString(),
            sourceFiles = moduleData.sources,
            javaSourceRoots = moduleData.javaSourceRoots.map { JvmSourceRoot(it.path, it.packagePrefix) },
            classpathRoots = moduleData.classpath,
            commonSourceFiles = emptyList(),
            modularJdkRoot = moduleData.modularJdkRoot,
            "java-production",
            isTests = false,
            emptySet(),
            friendDirs = moduleData.friendDirs,
            isIncrementalCompilation = true
        )
        val modulesFile = tmp.toFile().resolve("modules.xml")
        modulesFile.writeText(builder.asText().toString())
        args.buildFile = modulesFile.absolutePath
        args.jdkHome = moduleData.jdkHome?.absolutePath
    }

    abstract fun configureArguments(args: K2JVMCompilerArguments, moduleData: ModuleData)

    protected open fun handleResult(result: ExitCode, moduleData: ModuleData, collector: TestMessageCollector, targetInfo: String): ProcessorAction {
        val status = ModuleStatus(moduleData, targetInfo)
        totalModules += status

        return when (result) {
            ExitCode.OK -> {
                okModules += status
                ProcessorAction.NEXT
            }
            ExitCode.COMPILATION_ERROR -> {
                errorModules += status
                status.compilationError = collector.messages.firstOrNull {
                    it.severity == CompilerMessageSeverity.ERROR
                }?.message
                status.jvmInternalError = collector.messages.firstOrNull {
                    it.severity == CompilerMessageSeverity.EXCEPTION
                }?.message
                ProcessorAction.NEXT
            }
            ExitCode.INTERNAL_ERROR -> {
                crashedModules += status
                status.exceptionMessage = collector.messages.firstOrNull {
                    it.severity == CompilerMessageSeverity.EXCEPTION
                }?.message?.split("\n")?.let { exceptionLines ->
                    exceptionLines.lastOrNull { it.startsWith("Caused by: ") } ?: exceptionLines.firstOrNull()
                } ?: "NO MESSAGE"
                ProcessorAction.NEXT
            }
            else -> ProcessorAction.NEXT
        }
    }


    private fun String.shorten(): String {
        val split = split("\n")
        return split.mapIndexedNotNull { index, s ->
            if (index < 4 || index >= split.size - 6) s else null
        }.joinToString("\n")
    }

    open fun formatReport(stream: PrintStream, finalReport: Boolean) {
        val writer = PrintWriter("/home/ivankochurkin/Documents/JetBrains/performance/" + (if (useK2) "k2" else "k1") + "-performance-report.json")
        val jsonSerializer = Json { prettyPrint = true }
        val json = jsonSerializer.encodeToString(totalPassResult)
        writer.write(json)
        writer.close()

        stream.println("TOTAL MODULES: ${totalModules.size}")
        stream.println("OK MODULES: ${okModules.size}")
        stream.println("FAILED MODULES: ${totalModules.size - okModules.size}")

        formatReportTable(stream, "TOTAL", totalPassResult.values.fold(CumulativeTime()) { acc, time -> acc + time })

        if (finalReport) {
            with(stream) {
                println()
                println("SUCCESSFUL MODULES")
                println("------------------")
                println()
                for (okModule in okModules) {
                    println("${okModule.data.qualifiedName}: ${okModule.targetInfo}")
                }
                println()
                println("COMPILATION ERRORS")
                println("------------------")
                println()
                for (errorModule in errorModules.filter { it.jvmInternalError == null }) {
                    println("${errorModule.data.qualifiedName}: ${errorModule.targetInfo}")
                    println("        1st error: ${errorModule.compilationError}")
                }
                println()
                println("JVM INTERNAL ERRORS")
                println("------------------")
                println()
                for (errorModule in errorModules.filter { it.jvmInternalError != null }) {
                    println("${errorModule.data.qualifiedName}: ${errorModule.targetInfo}")
                    println("        1st error: ${errorModule.jvmInternalError?.shorten()}")
                }
                val crashedModuleGroups = crashedModules.groupBy { it.exceptionMessage.take(60) }
                for (modules in crashedModuleGroups.values) {
                    println()
                    println(modules.first().exceptionMessage)
                    println("--------------------------------------------------------")
                    println()
                    for (module in modules) {
                        println("${module.data.qualifiedName}: ${module.targetInfo}")
                        println("        ${module.exceptionMessage}")
                    }
                }
            }
        }
    }

    override fun processModule(moduleData: ModuleData): ProcessorAction {
        val compiler = K2JVMCompiler()
        val args = compiler.createArguments()
        val tmp = Files.createTempDirectory("compile-output")
        configureBaseArguments(args, moduleData, tmp)
        configureArguments(args, moduleData)

        val manager = CompilerPerformanceManager()
        val services = Services.Builder().register(CommonCompilerPerformanceManager::class.java, manager).build()
        val collector = TestMessageCollector()
        val result = try {
            CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.value = "true"
            compiler.exec(collector, services, args)
        } catch (e: Exception) {
            e.printStackTrace()
            ExitCode.INTERNAL_ERROR
        }
        val resultTime = manager.reportCumulativeTime()
        PerformanceCounter.resetAllCounters()

        tmp.toFile().deleteRecursively()
        if (result == ExitCode.OK) {
            totalPassResult[moduleData.name] = resultTime
        }

        return handleResult(result, moduleData, collector, manager.getTargetInfo())
    }

    protected fun createReport(finalReport: Boolean) {
        formatReport(System.out, finalReport)

        PrintStream(
            FileOutputStream(
                reportDir().resolve("report-$reportDateStr.log"),
                true
            )
        ).use { stream ->
            formatReport(stream, finalReport)
            stream.println()
            stream.println()
        }
    }


    private inner class CompilerPerformanceManager : CommonCompilerPerformanceManager("Modularized test performance manager") {

        fun reportCumulativeTime(): CumulativeTime {
            val gcInfo = measurements.filterIsInstance<GarbageCollectionMeasurement>()
                .associate { it.garbageCollectionKind to GCInfo(it.garbageCollectionKind, it.milliseconds, it.count) }

            val analysisMeasurement = measurements.filterIsInstance<CodeAnalysisMeasurement>().firstOrNull()
            val initMeasurement = measurements.filterIsInstance<CompilerInitializationMeasurement>().firstOrNull()
            val irMeasurements = measurements.filterIsInstance<IRMeasurement>()

            val components = buildMap {
                put("Init", initMeasurement?.milliseconds ?: 0)
                put("Analysis", analysisMeasurement?.milliseconds ?: 0)

                irMeasurements.firstOrNull { it.kind == IRMeasurement.Kind.TRANSLATION }?.milliseconds?.let { put("Translation", it) }
                irMeasurements.firstOrNull { it.kind == IRMeasurement.Kind.LOWERING }?.milliseconds?.let { put("Lowering", it) }

                val generationTime =
                    irMeasurements.firstOrNull { it.kind == IRMeasurement.Kind.GENERATION }?.milliseconds ?:
                    measurements.filterIsInstance<CodeGenerationMeasurement>().firstOrNull()?.milliseconds

                if (generationTime != null) {
                    put("Generation", generationTime)
                }
            }

            return CumulativeTime(
                gcInfo,
                components,
                files ?: 0,
                lines ?: 0
            )
        }
    }

    protected class TestMessageCollector : MessageCollector {

        data class Message(val severity: CompilerMessageSeverity, val message: String, val location: CompilerMessageSourceLocation?)

        val messages = arrayListOf<Message>()

        override fun clear() {
            messages.clear()
        }

        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
            messages.add(Message(severity, message, location))
            if (severity in CompilerMessageSeverity.VERBOSE) return
            println(MessageRenderer.GRADLE_STYLE.render(severity, message, location))
        }

        override fun hasErrors(): Boolean = messages.any {
            it.severity == CompilerMessageSeverity.EXCEPTION || it.severity == CompilerMessageSeverity.ERROR
        }
    }


}
