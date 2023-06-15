/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.compiler

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClassSymbol
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.expressions.FirResolvable
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.labelName
import org.jetbrains.kotlin.fir.references.FirSuperReference
import org.jetbrains.kotlin.fir.references.FirThisReference
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.constructFunctionType
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.specialFunctionTypeKind
import org.jetbrains.kotlin.fir.types.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid
import org.jetbrains.kotlin.name.StandardClassIds
import java.util.*
import kotlin.collections.LinkedHashMap

class CodeFragmentCapturedSymbol(
    val value: CodeFragmentCapturedValue,
    val symbol: FirBasedSymbol<*>,
    val typeRef: FirTypeRef
)

object CodeFragmentCapturedValueAnalyzer {
    fun analyze(session: FirSession, codeFragment: FirCodeFragment): List<CodeFragmentCapturedSymbol> {
        val selfSymbols = CodeFragmentDeclarationCollector().apply { codeFragment.accept(this) }.symbols.toSet()
        return CodeFragmentCapturedValueVisitor(session, selfSymbols).apply { codeFragment.accept(this) }.values
    }
}

private class CodeFragmentDeclarationCollector : FirDefaultVisitorVoid() {
    private val collectedSymbols = mutableListOf<FirBasedSymbol<*>>()

    val symbols: List<FirBasedSymbol<*>>
        get() = Collections.unmodifiableList(collectedSymbols)

    override fun visitElement(element: FirElement) {
        element.acceptChildren(this)
    }

    override fun visitClass(klass: FirClass) {
        collectedSymbols += klass.symbol
        super.visitClass(klass)
    }

    override fun visitFunction(function: FirFunction) {
        collectedSymbols += function.symbol
        super.visitFunction(function)
    }

    override fun visitValueParameter(valueParameter: FirValueParameter) {
        collectedSymbols += valueParameter.symbol
        super.visitValueParameter(valueParameter)
    }

    override fun visitVariable(variable: FirVariable) {
        collectedSymbols += variable.symbol
        super.visitVariable(variable)
    }
}

private class CodeFragmentCapturedValueVisitor(
    private val session: FirSession,
    private val selfSymbols: Set<FirBasedSymbol<*>>,
) : FirDefaultVisitorVoid() {
    private val mappings = LinkedHashMap<FirBasedSymbol<*>, CodeFragmentCapturedSymbol>()
    private val assignmentLhs = mutableListOf<FirBasedSymbol<*>>()

    val values: List<CodeFragmentCapturedSymbol>
        get() = mappings.values.toList()

    override fun visitElement(element: FirElement) {
        processElement(element)

        val lhs = (element as? FirVariableAssignment)?.lValue?.toResolvedCallableSymbol()
        if (lhs != null) {
            assignmentLhs.add(lhs)
        }

        element.acceptChildren(this)

        if (lhs != null) {
            require(assignmentLhs.removeLast() == lhs)
        }
    }

    private fun processElement(element: FirElement) {
        when (element) {
            is FirSuperReference -> {
                val symbol = (element.superTypeRef as? FirResolvedTypeRef)?.toRegularClassSymbol(session)
                if (symbol != null && symbol !in selfSymbols) {
                    val capturedValue = CodeFragmentCapturedValue.SuperClass(symbol.classId)
                    mappings[symbol] = CodeFragmentCapturedSymbol(capturedValue, symbol, element.superTypeRef)
                }
            }
            is FirThisReference -> {
                val symbol = element.boundSymbol
                if (symbol != null && symbol !in selfSymbols) {
                    when (symbol) {
                        is FirRegularClassSymbol -> {
                            val capturedValue = CodeFragmentCapturedValue.ContainingClass(symbol.classId)
                            val typeRef = buildResolvedTypeRef { type = symbol.defaultType() }
                            mappings[symbol] = CodeFragmentCapturedSymbol(capturedValue, symbol, typeRef)
                        }
                        is FirFunctionSymbol<*> -> {
                            if (element.contextReceiverNumber >= 0) {
                                val contextReceiver = symbol.resolvedContextReceivers[element.contextReceiverNumber]
                                val labelName = contextReceiver.labelName
                                if (labelName != null) {
                                    val capturedValue = CodeFragmentCapturedValue.ContextReceiver(labelName)
                                    mappings[symbol] = CodeFragmentCapturedSymbol(capturedValue, symbol, contextReceiver.typeRef)
                                }
                            } else {
                                val labelName = element.labelName ?: (symbol as? FirAnonymousFunctionSymbol)?.label?.name
                                val typeRef = symbol.receiverParameter?.typeRef ?: error("Receiver parameter not found")
                                if (labelName != null) {
                                    val capturedValue = CodeFragmentCapturedValue.ExtensionReceiver(labelName)
                                    mappings[symbol] = CodeFragmentCapturedSymbol(capturedValue, symbol, typeRef)
                                }
                            }
                        }
                    }

                }
            }
            is FirResolvable -> {
                val symbol = element.calleeReference.toResolvedCallableSymbol()
                if (symbol != null && symbol !in selfSymbols) {
                    processCallable(symbol)
                }
            }
        }
    }

    private fun processCallable(symbol: FirCallableSymbol<*>) {
        when (symbol) {
            is FirValueParameterSymbol -> {
                val capturedValue = CodeFragmentCapturedValue.Local(symbol.name, symbol.isMutated)
                mappings[symbol] = CodeFragmentCapturedSymbol(capturedValue, symbol, symbol.resolvedReturnTypeRef)
            }
            is FirPropertySymbol -> {
                if (symbol.isLocal) {
                    val capturedValue = when {
                        symbol.hasDelegate -> CodeFragmentCapturedValue.LocalDelegate(symbol.name, symbol.isMutated)
                        else -> CodeFragmentCapturedValue.Local(symbol.name, symbol.isMutated)
                    }
                    mappings[symbol] = CodeFragmentCapturedSymbol(capturedValue, symbol, symbol.resolvedReturnTypeRef)
                }
            }
            is FirNamedFunctionSymbol -> {
                if (symbol.isLocal) {
                    val capturedValue = CodeFragmentCapturedValue.LocalFunction(symbol.name)
                    val firFunction = symbol.fir
                    val functionType = firFunction.constructFunctionType(firFunction.specialFunctionTypeKind(session))
                    val typeRef = buildResolvedTypeRef { type = functionType }
                    mappings[symbol] = CodeFragmentCapturedSymbol(capturedValue, symbol, typeRef)
                }
            }
        }

        if (symbol.callableId == StandardClassIds.Callables.coroutineContext) {
            val capturedValue = CodeFragmentCapturedValue.CoroutineContext
            mappings[symbol] = CodeFragmentCapturedSymbol(capturedValue, symbol, symbol.resolvedReturnTypeRef)
        }
    }

    private val FirBasedSymbol<*>.isMutated: Boolean
        get() = assignmentLhs.lastOrNull() == this
}