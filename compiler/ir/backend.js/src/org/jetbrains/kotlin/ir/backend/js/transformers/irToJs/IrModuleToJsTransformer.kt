/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.transformers.irToJs

import org.jetbrains.kotlin.backend.common.serialization.checkIsFunctionInterface
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.ir.backend.js.*
import org.jetbrains.kotlin.ir.backend.js.export.*
import org.jetbrains.kotlin.ir.backend.js.lower.JsCodeOutliningLowering
import org.jetbrains.kotlin.ir.backend.js.lower.StaticMembersLowering
import org.jetbrains.kotlin.ir.backend.js.lower.isBuiltInClass
import org.jetbrains.kotlin.ir.backend.js.utils.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.js.backend.JsToStringGenerationVisitor
import org.jetbrains.kotlin.js.backend.NoOpSourceLocationConsumer
import org.jetbrains.kotlin.js.backend.SourceLocationConsumer
import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.SourceMapSourceEmbedding
import org.jetbrains.kotlin.js.sourceMap.SourceFilePathResolver
import org.jetbrains.kotlin.js.sourceMap.SourceMap3Builder
import org.jetbrains.kotlin.js.sourceMap.SourceMapBuilderConsumer
import org.jetbrains.kotlin.js.util.TextOutputImpl
import org.jetbrains.kotlin.serialization.js.ModuleKind
import org.jetbrains.kotlin.utils.memoryOptimizedMap
import org.jetbrains.kotlin.utils.addToStdlib.runIf
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.io.File
import java.util.*

val String.safeModuleName: String
    get() {
        var result = this

        if (result.startsWith('<')) result = result.substring(1)
        if (result.endsWith('>')) result = result.substring(0, result.length - 1)

        return sanitizeName("kotlin_$result", false)
    }

val IrModuleFragment.safeName: String
    get() = name.asString().safeModuleName

enum class JsGenerationGranularity {
    WHOLE_PROGRAM,
    PER_MODULE,
    PER_FILE
}

enum class TranslationMode(
    val production: Boolean,
    val granularity: JsGenerationGranularity,
    val minimizedMemberNames: Boolean,
) {
    FULL_DEV(production = false, granularity = JsGenerationGranularity.WHOLE_PROGRAM, minimizedMemberNames = false),
    FULL_PROD(production = true, granularity = JsGenerationGranularity.WHOLE_PROGRAM, minimizedMemberNames = false),
    FULL_PROD_MINIMIZED_NAMES(production = true, granularity = JsGenerationGranularity.WHOLE_PROGRAM, minimizedMemberNames = true),
    PER_MODULE_DEV(production = false, granularity = JsGenerationGranularity.PER_MODULE, minimizedMemberNames = false),
    PER_MODULE_PROD(production = true, granularity = JsGenerationGranularity.PER_MODULE, minimizedMemberNames = false),
    PER_MODULE_PROD_MINIMIZED_NAMES(production = true, granularity = JsGenerationGranularity.PER_MODULE, minimizedMemberNames = true),
    PER_FILE_DEV(production = false, granularity = JsGenerationGranularity.PER_FILE, minimizedMemberNames = false),
    PER_FILE_PROD(production = true, granularity = JsGenerationGranularity.PER_FILE, minimizedMemberNames = false),
    PER_FILE_PROD_MINIMIZED_NAMES(production = true, granularity = JsGenerationGranularity.PER_FILE, minimizedMemberNames = true);

    companion object {
        fun fromFlags(
            production: Boolean,
            granularity: JsGenerationGranularity,
            minimizedMemberNames: Boolean
        ): TranslationMode {
            return when (granularity) {
                JsGenerationGranularity.PER_MODULE ->
                    if (production) {
                        if (minimizedMemberNames) PER_MODULE_PROD_MINIMIZED_NAMES
                        else PER_MODULE_PROD
                    } else PER_MODULE_DEV

                JsGenerationGranularity.PER_FILE ->
                    if (production) {
                        if (minimizedMemberNames) PER_FILE_PROD_MINIMIZED_NAMES
                        else PER_FILE_PROD
                    } else PER_FILE_DEV

                JsGenerationGranularity.WHOLE_PROGRAM ->
                    if (production) {
                        if (minimizedMemberNames) FULL_PROD_MINIMIZED_NAMES
                        else FULL_PROD
                    } else FULL_DEV
            }
        }
    }
}

class JsCodeGenerator(
    private val program: JsIrProgram,
    private val granularity: JsGenerationGranularity,
    private val mainModuleName: String,
    private val moduleKind: ModuleKind,
    private val sourceMapsInfo: SourceMapsInfo?
) {
    fun generateJsCode(relativeRequirePath: Boolean, outJsProgram: Boolean): CompilationOutputsBuilt {
        return generateWrappedModuleBody(
            granularity,
            mainModuleName,
            moduleKind,
            program,
            sourceMapsInfo,
            relativeRequirePath,
            outJsProgram
        )
    }
}

class IrModuleToJsTransformer(
    private val backendContext: JsIrBackendContext,
    private val mainArguments: List<String>?,
    moduleToName: Map<IrModuleFragment, String> = emptyMap(),
    private val removeUnusedAssociatedObjects: Boolean = true,
) {
    private val shouldGeneratePolyfills = backendContext.configuration.getBoolean(JSConfigurationKeys.GENERATE_POLYFILLS)
    private val generateRegionComments = backendContext.configuration.getBoolean(JSConfigurationKeys.GENERATE_REGION_COMMENTS)
    private val shouldGenerateTypeScriptDefinitions = backendContext.configuration.getBoolean(JSConfigurationKeys.GENERATE_DTS)

    private val mainModuleName = backendContext.configuration[CommonConfigurationKeys.MODULE_NAME]!!
    private val moduleKind = backendContext.configuration[JSConfigurationKeys.MODULE_KIND]!!
    private val isEsModules = moduleKind == ModuleKind.ES
    private val sourceMapInfo = SourceMapsInfo.from(backendContext.configuration)

    private val moduleFragmentToNameMapper = ModuleFragmentToExternalName(moduleToName)

    private class IrFileExports(
        val file: IrFile,
        val exports: List<ExportedDeclaration>,
        val tsDeclarations: TypeScriptFragment?,
        val generatedFrom: IrFile? = null
    )

    private class IrAndExportedDeclarations(val fragment: IrModuleFragment, val files: List<IrFileExports>)

    private fun associateIrAndExport(modules: Iterable<IrModuleFragment>): List<IrAndExportedDeclarations> {
        val exportModelGenerator = ExportModelGenerator(backendContext, generateNamespacesForPackages = !isEsModules)

        return modules.map { module ->
            val files = exportModelGenerator.generateExportWithExternals(module.files)
            IrAndExportedDeclarations(module, files)
        }
    }

    private fun doStaticMembersLowering(modules: Iterable<IrModuleFragment>) {
        modules.forEach { module ->
            module.files.forEach {
                it.accept(backendContext.keeper, Keeper.KeepData(classInKeep = false, classShouldBeKept = false))
            }
        }

        modules.forEach { module ->
            module.files.forEach {
                StaticMembersLowering(backendContext).lower(it)
            }
        }
    }

    fun generateModule(modules: Iterable<IrModuleFragment>, modes: Set<TranslationMode>, relativeRequirePath: Boolean): CompilerResult {
        val exportData = associateIrAndExport(modules)
        doStaticMembersLowering(modules)

        val result = EnumMap<TranslationMode, CompilationOutputs>(TranslationMode::class.java)

        modes.filter { !it.production }.forEach {
            result[it] = makeJsCodeGeneratorFromIr(exportData, it).generateJsCode(relativeRequirePath, true)
        }

        if (modes.any { it.production }) {
            optimizeProgramByIr(modules, backendContext, removeUnusedAssociatedObjects)
        }

        modes.filter { it.production }.forEach {
            result[it] = makeJsCodeGeneratorFromIr(exportData, it).generateJsCode(relativeRequirePath, true)
        }

        return CompilerResult(result)
    }

    fun makeJsCodeGenerator(modules: Iterable<IrModuleFragment>, mode: TranslationMode): JsCodeGenerator {
        val exportData = associateIrAndExport(modules)
        doStaticMembersLowering(modules)

        if (mode.production) {
            optimizeProgramByIr(modules, backendContext, removeUnusedAssociatedObjects)
        }

        return makeJsCodeGeneratorFromIr(exportData, mode)
    }

    fun makeIrFragmentsGenerators(
        dirtyFiles: Collection<IrFile>,
        allModules: Collection<IrModuleFragment>
    ): List<() -> List<JsIrProgramFragment>> {
        val files = dirtyFiles + backendContext.mapping.chunkToOriginalFile.keys
        val exportModelGenerator = ExportModelGenerator(backendContext, generateNamespacesForPackages = !isEsModules)
        val exportData = exportModelGenerator.generateExportWithExternals(files)
        val mode = TranslationMode.fromFlags(production = false, backendContext.granularity, minimizedMemberNames = false)

        doStaticMembersLowering(allModules)

        return exportData
            .groupBy { it.generatedFrom ?: it.file }
            .map {
                { it.value.flatMap { generateProgramFragment(it, mode) } }
            }
    }

    private fun ExportModelGenerator.generateExportWithExternals(irFiles: Collection<IrFile>): List<IrFileExports> {
        return irFiles.map { irFile ->
            val exports = generateExport(irFile)
            val additionalExports = backendContext.externalPackageFragment[irFile.symbol]?.let { generateExport(it) } ?: emptyList()
            val allExports = additionalExports + exports
            val tsDeclarations = runIf(shouldGenerateTypeScriptDefinitions) {
                allExports.ifNotEmpty { toTypeScriptFragment(moduleKind) }
            }
            IrFileExports(irFile, allExports, tsDeclarations, context.mapping.chunkToOriginalFile[irFile])
        }
    }

    private fun makeJsCodeGeneratorFromIr(exportData: List<IrAndExportedDeclarations>, mode: TranslationMode): JsCodeGenerator {
        if (mode.minimizedMemberNames) {
            backendContext.fieldDataCache.clear()
            backendContext.minimizedNameGenerator.clear()
        }

        val program = when (mode.granularity) {
            JsGenerationGranularity.WHOLE_PROGRAM, JsGenerationGranularity.PER_MODULE -> generateJsIrProgramPerModule(exportData, mode)
            JsGenerationGranularity.PER_FILE -> generateJsIrProgramPerFile(exportData, mode)
        }

        return JsCodeGenerator(program, mode.granularity, mainModuleName, moduleKind, sourceMapInfo)
    }

    private fun generateJsIrProgramPerModule(exportData: List<IrAndExportedDeclarations>, mode: TranslationMode): JsIrProgram {
        val mainModule = exportData.last()

        return JsIrProgram(
            exportData.map { data ->
                JsIrModule(
                    data.fragment.safeName,
                    moduleFragmentToNameMapper.getExternalNameFor(data.fragment),
                    data.files.flatMap { generateProgramFragment(it, mode) },
                    mainModule.fragment.safeName.takeIf { !isEsModules && data != mainModule }
                )
            }
        )
    }

    private fun generateJsIrProgramPerFile(exportData: List<IrAndExportedDeclarations>, mode: TranslationMode): JsIrProgram {
        val modulesPerFile = buildList {
            for (module in exportData) {
                var hasFileWithJsExportedDeclaration = false

                for (fileExports in module.files) {
                    if (fileExports.file.couldBeSkipped()) continue
                    val (programFragment, exportProgramFragment) = generateProgramFragment(fileExports, mode)

                    add(fileExports.toJsIrModule(mode, programFragment))

                    if (fileExports.exports.isNotEmpty()) {
                        add(fileExports.toJsIrModuleForExport(module, mode, exportProgramFragment))
                        hasFileWithJsExportedDeclaration = true
                    }
                }

                if (hasFileWithJsExportedDeclaration) {
                    add(module.toJsIrProxyModule())
                }
            }
        }

        return JsIrProgram(modulesPerFile)
    }

    private fun IrFileExports.toJsIrModule(mode: TranslationMode, programFragment: JsIrProgramFragment): JsIrModule {
        return JsIrModule(
            moduleFragmentToNameMapper.getSafeNameFor(file),
            moduleFragmentToNameMapper.getExternalNameFor(file, mode.granularity),
            listOf(programFragment),
        )
    }

    private fun IrFileExports.toJsIrModuleForExport(
        module: IrAndExportedDeclarations,
        mode: TranslationMode,
        programFragment: JsIrProgramFragment
    ): JsIrModule {
        return JsIrModule(
            moduleFragmentToNameMapper.getSafeNameExporterFor(file),
            moduleFragmentToNameMapper.getExternalNameForExporterFile(file, mode.granularity),
            listOf(programFragment),
            module.fragment.safeName
        )
    }

    private fun IrAndExportedDeclarations.toJsIrProxyModule(): JsIrModule {
        return JsIrModule(
            fragment.safeName,
            moduleFragmentToNameMapper.getExternalNameFor(fragment),
            listOf(JsIrProgramFragment("<proxy-file>"))
        )
    }

    private val generateFilePaths = backendContext.configuration.getBoolean(JSConfigurationKeys.GENERATE_COMMENTS_WITH_FILE_PATH)
    private val pathPrefixMap = backendContext.configuration.getMap(JSConfigurationKeys.FILE_PATHS_PREFIX_MAP)
    private val optimizeGeneratedJs = backendContext.configuration.get(JSConfigurationKeys.OPTIMIZE_GENERATED_JS, true)

    private fun IrFileExports.generateProgramFragmentForExport(
        mode: TranslationMode,
        nameScope: NameTable<IrDeclaration>
    ): JsIrProgramFragment {
        val globalNames = NameTable<String>(nameScope)
        val nameGenerator = JsNameLinkingNamer(backendContext, mode.minimizedMemberNames, isEsModules)
        val internalModuleName = ReservedJsNames.makeInternalModuleName().takeIf { !isEsModules }
        val staticContext = JsStaticContext(backendContext, nameGenerator, nameScope, mode)

        return JsIrProgramFragment(file.packageFqName.asString()).apply {
            dts = tsDeclarations
            exports.statements += ExportModelToJsStatements(staticContext, backendContext.es6mode, { globalNames.declareFreshName(it, it) })
                .generateModuleExport(
                    ExportedModule(mainModuleName, moduleKind, this@generateProgramFragmentForExport.exports),
                    internalModuleName,
                    isEsModules
                )
            computeAndSaveNameBindings(emptySet(), nameGenerator)
        }
    }

    private fun generateProgramFragment(fileExports: IrFileExports, mode: TranslationMode): List<JsIrProgramFragment> {
        val globalNameScope = NameTable<IrDeclaration>()
        val nameGenerator = JsNameLinkingNamer(backendContext, mode.minimizedMemberNames, isEsModules)
        val staticContext = JsStaticContext(backendContext, nameGenerator, globalNameScope, mode)

        val result = JsIrProgramFragment(fileExports.file.packageFqName.asString()).apply {
            if (shouldGeneratePolyfills) {
                polyfills.statements += backendContext.polyfills.getAllPolyfillsFor(fileExports.file)
            }
        }

        val statements = result.declarations.statements
        val fileStatements = fileExports.file.accept(IrFileToJsTransformer(useBareParameterNames = true), staticContext).statements
        val exportFragment = fileExports.generateProgramFragmentForExport(mode, globalNameScope)

        if (fileStatements.isNotEmpty()) {
            var startComment = ""

            if (generateRegionComments) {
                startComment = "region "
            }

            if (generateRegionComments || generateFilePaths) {
                val originalPath = fileExports.file.path
                val path = pathPrefixMap.entries
                    .find { (k, _) -> originalPath.startsWith(k) }
                    ?.let { (k, v) -> v + originalPath.substring(k.length) }
                    ?: originalPath

                startComment += "file: $path"
            }

            if (startComment.isNotEmpty()) {
                statements.add(JsSingleLineComment(startComment))
            }

            statements.addAll(fileStatements)
            if (generateRegionComments) {
                statements += JsSingleLineComment("endregion")
            }
        }

        staticContext.classModels.entries.forEach { (symbol, model) ->
            result.classes[nameGenerator.getNameForClass(symbol.owner)] =
                JsIrIcClassModel(model.superClasses.memoryOptimizedMap { staticContext.getNameForClass(it.owner) }).also {
                    it.preDeclarationBlock.statements += model.preDeclarationBlock.statements
                    it.postDeclarationBlock.statements += model.postDeclarationBlock.statements
                }
        }

        result.initializers.statements += staticContext.initializerBlock.statements

        if (mainArguments != null) {
            JsMainFunctionDetector(backendContext).getMainFunctionOrNull(fileExports.file)?.let {
                val jsName = staticContext.getNameForStaticFunction(it)
                val generateArgv = it.valueParameters.firstOrNull()?.isStringArrayParameter() ?: false
                val generateContinuation = it.isLoweredSuspendFunction(backendContext)
                result.mainFunction = JsInvocation(jsName.makeRef(), generateMainArguments(generateArgv, generateContinuation, staticContext)).makeStmt()
            }
        }

        backendContext.testFunsPerFile[fileExports.file]?.let {
            result.testFunInvocation = JsInvocation(staticContext.getNameForStaticFunction(it).makeRef()).makeStmt()
            result.suiteFn = staticContext.getNameForStaticFunction(backendContext.suiteFun!!.owner)
        }

        result.importedModules += nameGenerator.importedModules

        val definitionSet = fileExports.file.declarations.toSet()

        result.computeAndSaveNameBindings(definitionSet, nameGenerator)
        result.computeAndSaveImports(definitionSet, nameGenerator)
        result.computeAndSaveDefinitions(definitionSet, fileExports)

        if (optimizeGeneratedJs) {
            optimizeFragmentByJsAst(result)
        }

        return listOf(result, exportFragment)
    }

    private fun Set<IrDeclaration>.computeTag(declaration: IrDeclaration): String? {
        val tag = (backendContext.irFactory as IdSignatureRetriever).declarationSignature(declaration)?.toString()

        if (tag == null && !contains(declaration)) {
            error("signature for ${declaration.render()} not found")
        }

        return tag
    }

    private fun JsIrProgramFragment.computeAndSaveNameBindings(
        definitions: Set<IrDeclaration>,
        nameGenerator: JsNameLinkingNamer
    ) {
        nameGenerator.nameMap.entries.forEach { (declaration, name) ->
            definitions.computeTag(declaration)?.let { tag ->
                nameBindings[tag] = name
                if (isBuiltInClass(declaration) || checkIsFunctionInterface(declaration.symbol.signature)) {
                    optionalCrossModuleImports += tag
                }
            }
        }
    }

    private fun JsIrProgramFragment.computeAndSaveImports(
        definitions: Set<IrDeclaration>,
        nameGenerator: JsNameLinkingNamer
    ) {
        nameGenerator.imports.entries.forEach { (declaration, importExpression) ->
            val tag = definitions.computeTag(declaration) ?: error("No tag for imported declaration ${declaration.render()}")
            imports[tag] = importExpression
            optionalCrossModuleImports += tag
        }
    }

    private fun JsIrProgramFragment.computeAndSaveDefinitions(
        definitions: Set<IrDeclaration>,
        fileExports: IrFileExports,
    ) {
        fileExports.file.declarations.forEach {
            definitions.computeTag(it)?.let { tag ->
                this.definitions += tag
            }

            if (it is IrClass && it.isInterface) {
                it.declarations.forEach {
                    definitions.computeTag(it)?.let { tag ->
                        this.definitions += tag
                    }
                }
            }
        }
    }

    private fun generateMainArguments(
        generateArgv: Boolean,
        generateContinuation: Boolean,
        staticContext: JsStaticContext,
    ): List<JsExpression> {
        val mainArguments = this.mainArguments!!
        val mainArgumentsArray =
            if (generateArgv) JsArrayLiteral(mainArguments.map { JsStringLiteral(it) }) else null

        val continuation = if (generateContinuation) {
            backendContext.coroutineEmptyContinuation.owner
                .let { it.getter!! }
                .let { staticContext.getNameForStaticFunction(it) }
                .let { JsInvocation(it.makeRef()) }
        } else null

        return listOfNotNull(mainArgumentsArray, continuation)
    }

    private fun IrFile.couldBeSkipped(): Boolean = declarations.all { it.origin == JsCodeOutliningLowering.OUTLINED_JS_CODE_ORIGIN }
}

private fun generateWrappedModuleBody(
    granularity: JsGenerationGranularity,
    mainModuleName: String,
    moduleKind: ModuleKind,
    program: JsIrProgram,
    sourceMapsInfo: SourceMapsInfo?,
    relativeRequirePath: Boolean,
    outJsProgram: Boolean
): CompilationOutputsBuilt {
    return when (granularity) {
        JsGenerationGranularity.WHOLE_PROGRAM -> generateSingleWrappedModuleBody(
            mainModuleName,
            moduleKind,
            program.asFragments(),
            sourceMapsInfo,
            generateCallToMain = true,
            outJsProgram = outJsProgram
        )
        JsGenerationGranularity.PER_FILE,
        JsGenerationGranularity.PER_MODULE -> generateMultiWrappedModuleBody(
            mainModuleName,
            moduleKind,
            program,
            sourceMapsInfo,
            relativeRequirePath,
            outJsProgram
        )
    }
}

private fun generateMultiWrappedModuleBody(
    mainModuleName: String,
    moduleKind: ModuleKind,
    program: JsIrProgram,
    sourceMapsInfo: SourceMapsInfo?,
    relativeRequirePath: Boolean,
    outJsProgram: Boolean
): CompilationOutputsBuilt {
    // mutable container allows explicitly remove elements from itself,
    // so we are able to help GC to free heavy JsIrModule objects
    // TODO: It makes sense to invent something better, because this logic can be easily broken
    val moduleToRef = program.asCrossModuleDependencies(moduleKind, relativeRequirePath).toMutableList()

    val mainModule = moduleToRef.removeLast().let { (main, mainRef) ->
        generateSingleWrappedModuleBody(
            mainModuleName,
            moduleKind,
            main.fragments,
            sourceMapsInfo,
            generateCallToMain = true,
            mainRef,
            outJsProgram
        )
    }

    mainModule.dependencies = buildList(moduleToRef.size) {
        while (moduleToRef.isNotEmpty()) {
            moduleToRef.removeFirst().let { (module, moduleRef) ->
                val moduleName = module.externalModuleName
                val moduleCompilationOutput = generateSingleWrappedModuleBody(
                    moduleName,
                    moduleKind,
                    module.fragments,
                    sourceMapsInfo,
                    generateCallToMain = false,
                    moduleRef,
                    outJsProgram
                )
                add(moduleName to moduleCompilationOutput)
            }
        }
    }

    return mainModule
}

fun generateSingleWrappedModuleBody(
    moduleName: String,
    moduleKind: ModuleKind,
    fragments: List<JsIrProgramFragment>,
    sourceMapsInfo: SourceMapsInfo?,
    generateCallToMain: Boolean,
    crossModuleReferences: CrossModuleReferences = CrossModuleReferences.Empty(moduleKind),
    outJsProgram: Boolean = true
): CompilationOutputsBuilt {
    val program = Merger(
        moduleName,
        moduleKind,
        fragments,
        crossModuleReferences,
        generateRegionComments = true,
        generateCallToMain,
    ).merge()

    program.resolveTemporaryNames()

    val jsCode = TextOutputImpl()

    val sourceMapBuilder: SourceMap3Builder?
    val sourceMapBuilderConsumer: SourceLocationConsumer
    if (sourceMapsInfo != null) {
        val sourceMapPrefix = sourceMapsInfo.sourceMapPrefix
        sourceMapBuilder = SourceMap3Builder(null, jsCode::getColumn, sourceMapPrefix)

        val pathResolver = SourceFilePathResolver.create(sourceMapsInfo.sourceRoots, sourceMapPrefix, sourceMapsInfo.outputDir)

        val sourceMapContentEmbedding =
            sourceMapsInfo.sourceMapContentEmbedding

        sourceMapBuilderConsumer = SourceMapBuilderConsumer(
            File("."),
            sourceMapBuilder,
            pathResolver,
            sourceMapContentEmbedding == SourceMapSourceEmbedding.ALWAYS,
            sourceMapContentEmbedding != SourceMapSourceEmbedding.NEVER
        )
    } else {
        sourceMapBuilder = null
        sourceMapBuilderConsumer = NoOpSourceLocationConsumer
    }

    program.accept(JsToStringGenerationVisitor(jsCode, sourceMapBuilderConsumer))

    return CompilationOutputsBuilt(
        jsCode.toString(),
        sourceMapBuilder?.build(),
        fragments.mapNotNull { it.dts }.ifNotEmpty { joinTypeScriptFragments() },
        program.takeIf { outJsProgram }
    )
}
