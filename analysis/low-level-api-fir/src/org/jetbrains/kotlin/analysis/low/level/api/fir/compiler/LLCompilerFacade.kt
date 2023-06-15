/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.compiler

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.*
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirResolvableModuleSession
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.*
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.CodegenFactory
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.constant.EvaluatedConstTracker
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.KtPsiDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.backend.*
import org.jetbrains.kotlin.fir.backend.jvm.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.pipeline.signatureComposerForJvmFir2Ir
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.references.toResolvedSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmIrMangler
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.source.PsiSourceFile
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.utils.addToStdlib.runIf

class LLCompilationResult(
    val outputFiles: List<OutputFile>,
    val diagnostics: List<KtPsiDiagnostic>,
    val capturedValues: List<CodeFragmentCapturedValue>
)

object LLCompilerFacade {
    val CODE_FRAGMENT_CLASS_NAME = CompilerConfigurationKey<String>("code fragment class name")
    val CODE_FRAGMENT_METHOD_NAME = CompilerConfigurationKey<String>("code fragment method name")

    fun compile(
        file: KtFile,
        configuration: CompilerConfiguration,
        languageVersionSettings: LanguageVersionSettings,
        classBuilderFactory: ClassBuilderFactory,
    ): Result<LLCompilationResult> {
        try {
            val project = file.project

            val effectiveConfiguration = configuration
                .copy()
                .apply { put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true) }

            val module = ProjectStructureProvider.getModule(project, file, contextualModule = null)
            val resolveSession = module.getFirResolveSession(project)
            val session = resolveSession.useSiteFirSession as LLFirResolvableModuleSession
            val scopeSession = session.moduleComponents.scopeSessionProvider.getScopeSession()

            val mainFirFile = resolveSession.getOrBuildFirFile(file)
            val inlineCollector = InlineFunctionCollectingVisitor().apply { process(mainFirFile) }

            val filesToCompile = inlineCollector.files
            val firFilesToCompile = filesToCompile.map { it.getOrBuildFir(resolveSession) as FirFile }

            val diagnostics = session.moduleComponents.diagnosticsCollector
                .collectDiagnosticsForFile(file, DiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)

            if (diagnostics.any { it.severity == Severity.ERROR && it.factory !in INSIGNIFICANT_ERRORS }) {
                return Result.success(LLCompilationResult(listOf(), diagnostics.toList(), listOf()))
            }

            val codeFragmentMappings = runIf(file is KtCodeFragment) {
                computeCodeFragmentMappings(mainFirFile, session, effectiveConfiguration)
            }

            val fir2IrExtension = LLFir2IrExtensions(
                delegate = JvmFir2IrExtensions(effectiveConfiguration, JvmIrDeserializerImpl(), JvmIrMangler),
                injectedValueProvider = codeFragmentMappings?.injectedValueProvider ?: { null }
            )

            val generateClassFilter = SingleFileGenerateClassFilter(file, inlineCollector.inlinedClasses)
            val codegenFactory = createJvmIrCodegenFactory(effectiveConfiguration)

            val fir2IrConfiguration = Fir2IrConfiguration(
                languageVersionSettings,
                linkViaSignatures = false,
                EvaluatedConstTracker.create(),
                allowSuddenDeclarations = true
            )

            val irGenerationExtensions = IrGenerationExtension.getInstances(project)

            val fir2IrResult = Fir2IrConverter.createModuleFragmentWithSignaturesIfNeeded(
                session,
                scopeSession,
                firFilesToCompile,
                fir2IrExtension,
                fir2IrConfiguration,
                JvmIrMangler,
                IrFactoryImpl,
                FirJvmVisibilityConverter,
                Fir2IrJvmSpecialAnnotationSymbolProvider(),
                irGenerationExtensions,
                DefaultBuiltIns.Instance,
                Fir2IrCommonMemberStorage(signatureComposerForJvmFir2Ir(false), FirJvmKotlinMangler()),
                initializedIrBuiltIns = null
            )

            ProgressManager.checkCanceled()

            val bindingContext = NoScopeRecordCliBindingTrace().bindingContext

            val generationState = GenerationState.Builder(
                project,
                classBuilderFactory,
                fir2IrResult.irModuleFragment.descriptor,
                bindingContext,
                filesToCompile,
                effectiveConfiguration,
            ).generateDeclaredClassFilter(generateClassFilter)
                .codegenFactory(codegenFactory)
                .build()

            try {
                generationState.beforeCompile()

                ProgressManager.checkCanceled()

                codegenFactory.generateModuleInFrontendIRMode(
                    generationState,
                    fir2IrResult.irModuleFragment,
                    fir2IrResult.components.symbolTable,
                    fir2IrResult.components.irProviders,
                    JvmFir2IrExtensions(effectiveConfiguration, JvmIrDeserializerImpl(), JvmIrMangler),
                    FirJvmBackendExtension(fir2IrResult.components, null),
                    fir2IrResult.pluginContext
                )

                CodegenFactory.doCheckCancelled(generationState)
                generationState.factory.done()

                val outputFiles = generationState.factory.asList()

                val backendDiagnostics = buildList {
                    for (diagnostic in generationState.collectedExtraJvmDiagnostics.all()) {
                        if (diagnostic.severity == Severity.ERROR) {
                            add(diagnostic as KtPsiDiagnostic)
                        }
                    }
                }

                val capturedValues = codeFragmentMappings?.capturedValues ?: emptyList()
                return Result.success(LLCompilationResult(outputFiles, backendDiagnostics, capturedValues))
            } finally {
                generationState.destroy()
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Throwable) {
            return Result.failure(e)
        }
    }

    private class CodeFragmentMappings(
        val capturedValues: List<CodeFragmentCapturedValue>,
        val injectedValueProvider: (FirReference) -> InjectedValue?
    )

    private fun computeCodeFragmentMappings(
        mainFirFile: FirFile,
        session: FirSession,
        configuration: CompilerConfiguration,
    ): CodeFragmentMappings {
        val codeFragment = mainFirFile.declarations.single() as FirCodeFragment

        val capturedSymbols = CodeFragmentCapturedValueAnalyzer.analyze(session, codeFragment)
        val capturedValues = capturedSymbols.map { it.value }

        val injectedSymbols = capturedSymbols.map { InjectedValue(it.symbol, it.typeRef, it.value.isMutated) }

        codeFragment.conversionData = CodeFragmentConversionData(
            classId = ClassId(FqName.ROOT, Name.identifier(configuration[CODE_FRAGMENT_CLASS_NAME] ?: "CodeFragment")),
            methodName = Name.identifier(configuration[CODE_FRAGMENT_METHOD_NAME] ?: "run"),
            injectedSymbols
        )

        val injectedSymbolMapping = injectedSymbols.associateBy { it.symbol }

        return CodeFragmentMappings(capturedValues) { calleeReference ->
            injectedSymbolMapping[calleeReference.toResolvedSymbol<FirBasedSymbol<*>>()]
        }
    }

    private class LLFir2IrExtensions(
        delegate: Fir2IrExtensions,
        private val injectedValueProvider: (FirReference) -> InjectedValue?
    ) : Fir2IrExtensions by delegate {
        override fun findInjectedValue(calleeReference: FirReference): InjectedValue? {
            return injectedValueProvider(calleeReference)
        }
    }

    private class SingleFileGenerateClassFilter(
        private val file: KtFile,
        private val inlinedClasses: Set<KtClassOrObject>
    ) : GenerationState.GenerateClassFilter() {
        private val filesWithInlinedClasses = inlinedClasses.mapTo(mutableSetOf()) { it.containingKtFile }

        override fun shouldGeneratePackagePart(ktFile: KtFile): Boolean {
            return file === ktFile || ktFile in filesWithInlinedClasses
        }

        override fun shouldAnnotateClass(processingClassOrObject: KtClassOrObject): Boolean {
            return true
        }

        override fun shouldGenerateClass(processingClassOrObject: KtClassOrObject): Boolean {
            return processingClassOrObject.containingKtFile === file ||
                    processingClassOrObject is KtObjectDeclaration && processingClassOrObject in inlinedClasses
        }

        override fun shouldGenerateScript(script: KtScript): Boolean {
            return script.containingKtFile === file
        }

        override fun shouldGenerateCodeFragment(script: KtCodeFragment) = false
    }

    private fun createJvmIrCodegenFactory(configuration: CompilerConfiguration): JvmIrCodegenFactory {
        val jvmGeneratorExtensions = object : JvmGeneratorExtensionsImpl(configuration) {
            override fun getContainerSource(descriptor: DeclarationDescriptor): DeserializedContainerSource? {
                // Stubbed top-level function IR symbols (from other source files in the module) require a parent facade class to be
                // generated, which requires a container source to be provided. Without a facade class, function IR symbols will have
                // an `IrExternalPackageFragment` parent, which trips up code generation during IR lowering.
                val psiSourceFile =
                    descriptor.toSourceElement.containingFile as? PsiSourceFile ?: return super.getContainerSource(descriptor)
                return FacadeClassSourceShimForFragmentCompilation(psiSourceFile)
            }
        }

        val ideCodegenSettings = JvmIrCodegenFactory.IdeCodegenSettings(
            shouldStubAndNotLinkUnboundSymbols = true,
            shouldDeduplicateBuiltInSymbols = true,

            // Because the file to compile may be contained in a "common" multiplatform module, an `expect` declaration doesn't necessarily
            // have an obvious associated `actual` symbol. `shouldStubOrphanedExpectSymbols` generates stubs for such `expect` declarations.
            shouldStubOrphanedExpectSymbols = true,

            // Likewise, the file to compile may be contained in a "platform" multiplatform module, where the `actual` declaration is
            // referenced in the symbol table automatically, but not its `expect` counterpart, because it isn't contained in the files to
            // compile. `shouldReferenceUndiscoveredExpectSymbols` references such `expect` symbols in the symbol table so that they can
            // subsequently be stubbed.
            shouldReferenceUndiscoveredExpectSymbols = false, // TODO it was true
        )

        return JvmIrCodegenFactory(
            configuration,
            PhaseConfig(jvmPhases),
            jvmGeneratorExtensions = jvmGeneratorExtensions,
            ideCodegenSettings = ideCodegenSettings,
        )
    }
}

private val INSIGNIFICANT_ERRORS = setOf(
    FirErrors.INVISIBLE_REFERENCE,
    FirErrors.DEPRECATION_ERROR,
    FirErrors.DIVISION_BY_ZERO,
    FirErrors.OPT_IN_USAGE_ERROR,
    FirErrors.OPT_IN_OVERRIDE_ERROR
)