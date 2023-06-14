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
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid
import org.jetbrains.kotlin.name.StandardClassIds
import java.util.*
import kotlin.collections.LinkedHashMap

object CodeFragmentCapturedValueAnalyzer {
    fun analyze(session: FirSession, codeFragment: FirCodeFragment): Map<FirBasedSymbol<*>, CodeFragmentCapturedValue> {
        val selfSymbols = CodeFragmentDeclarationCollector().apply { codeFragment.accept(this) }.symbols.toSet()
        return CodeFragmentCapturedValueVisitor(session, selfSymbols).apply { codeFragment.accept(this) }.values
    }
}

private class CodeFragmentDeclarationCollector : FirDefaultVisitorVoid() {
    private val mutableSymbols = mutableListOf<FirBasedSymbol<*>>()

    val symbols: List<FirBasedSymbol<*>>
        get() = Collections.unmodifiableList(mutableSymbols)

    override fun visitElement(element: FirElement) {
        element.acceptChildren(this)
    }

    override fun visitClass(klass: FirClass) {
        mutableSymbols += klass.symbol
        super.visitClass(klass)
    }

    override fun visitFunction(function: FirFunction) {
        mutableSymbols += function.symbol
        super.visitFunction(function)
    }

    override fun visitValueParameter(valueParameter: FirValueParameter) {
        mutableSymbols += valueParameter.symbol
        super.visitValueParameter(valueParameter)
    }

    override fun visitVariable(variable: FirVariable) {
        mutableSymbols += variable.symbol
        super.visitVariable(variable)
    }
}

private class CodeFragmentCapturedValueVisitor(
    private val session: FirSession,
    private val selfSymbols: Set<FirBasedSymbol<*>>,
) : FirDefaultVisitorVoid() {
    private val mutableValues = LinkedHashMap<FirBasedSymbol<*>, CodeFragmentCapturedValue>()

    private val assignmentLhs = mutableListOf<FirBasedSymbol<*>>()

    val values: Map<FirBasedSymbol<*>, CodeFragmentCapturedValue>
        get() = Collections.unmodifiableMap(mutableValues)

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
                    mutableValues[symbol] = CodeFragmentCapturedValue.SuperClass(symbol.classId)
                }
            }
            is FirThisReference -> {
                val symbol = element.boundSymbol
                if (symbol != null && symbol !in selfSymbols) {
                    when (symbol) {
                        is FirRegularClassSymbol -> {
                            mutableValues[symbol] = CodeFragmentCapturedValue.ContainingClass(symbol.classId)
                        }
                        is FirFunctionSymbol<*> -> {
                            if (element.contextReceiverNumber >= 0) {
                                val contextReceiver = symbol.resolvedContextReceivers[element.contextReceiverNumber]
                                val labelName = contextReceiver.labelName
                                if (labelName != null) {
                                    mutableValues[symbol] = CodeFragmentCapturedValue.ContextReceiver(labelName)
                                }
                            } else {
                                val labelName = element.labelName ?: (symbol as? FirAnonymousFunctionSymbol)?.label?.name
                                if (labelName != null) {
                                    mutableValues[symbol] = CodeFragmentCapturedValue.ExtensionReceiver(labelName)
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
                mutableValues[symbol] = CodeFragmentCapturedValue.Local(symbol.name, symbol.isMutated)
            }
            is FirPropertySymbol -> {
                if (symbol.isLocal) {
                    mutableValues[symbol] = when {
                        symbol.hasDelegate -> CodeFragmentCapturedValue.LocalDelegate(symbol.name, symbol.isMutated)
                        else -> CodeFragmentCapturedValue.Local(symbol.name, symbol.isMutated)
                    }
                }
            }
            is FirNamedFunctionSymbol -> {
                if (symbol.isLocal) {
                    mutableValues[symbol] = CodeFragmentCapturedValue.LocalFunction(symbol.name)
                }
            }
        }

        if (symbol.callableId == StandardClassIds.Callables.coroutineContext) {
            mutableValues[symbol] = CodeFragmentCapturedValue.CoroutineContext
        }
    }

    private val FirBasedSymbol<*>.isMutated: Boolean
        get() = assignmentLhs.lastOrNull() == this
}