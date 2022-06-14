/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.cfa

import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.contracts.description.canBeRevisited
import org.jetbrains.kotlin.contracts.description.isDefinitelyVisited
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.cfa.util.TraverseDirection
import org.jetbrains.kotlin.fir.analysis.cfa.util.traverse
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClassSymbol
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isLateInit
import org.jetbrains.kotlin.fir.declarations.utils.referredPropertySymbol
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.dfa.*
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.*
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol

@OptIn(SymbolInternals::class)
object FirPropertyInitializationAnalyzer : AbstractFirPropertyInitializationChecker() {
    override fun analyze(
        graphReference: FirControlFlowGraphReferenceImpl,
        reporter: DiagnosticReporter,
        data: PropertyInitializationInfoData,
        properties: Set<FirPropertySymbol>,
        capturedWrites: Set<FirVariableAssignment>,
        context: CheckerContext
    ) {
        val reporterVisitor = PropertyReporter(data, properties, capturedWrites, reporter, context, graphReference.dataFlowInfo)
        graphReference.controlFlowGraph.traverse(TraverseDirection.Forward, reporterVisitor)
    }

    private class PropertyReporter(
        val propertyInitializationInfoData: PropertyInitializationInfoData,
        val properties: Set<FirPropertySymbol>,
        val capturedWrites: Set<FirVariableAssignment>,
        val reporter: DiagnosticReporter,
        val context: CheckerContext,
        val dataFlowInfo: DataFlowInfo?
    ) : ControlFlowGraphVisitorVoid() {
        val classes = stackOf<FirClassSymbol<out FirClass>?>()
        val propertiesInClassInitializer = mutableSetOf<FirPropertySymbol>()
        val insideLambdaOrAnonymous = stackOf<Boolean>()
        val insidePropertyInitializer = stackOf<Boolean>()
        val insideInitBlock = stackOf<Boolean>()

        override fun visitNode(node: CFGNode<*>) {}

        override fun visitClassEnterNode(node: ClassEnterNode, data: Nothing?) {
            classes.push(node.fir.symbol)
        }

        override fun visitClassExitNode(node: ClassExitNode, data: Nothing?) {
            classes.pop()
        }

        override fun visitFunctionEnterNode(node: FunctionEnterNode) {
            val fir = node.fir
            if (fir is FirConstructor && fir.delegatedConstructor?.isThis != true) {
                classes.push(node.fir.returnTypeRef.toRegularClassSymbol(context.session))
            }
        }

        override fun visitFunctionExitNode(node: FunctionExitNode) {
            val fir = node.fir
            if (fir is FirConstructor && fir.delegatedConstructor?.isThis != true) {
                classes.pop()
            }
        }

        override fun visitPropertyInitializerEnterNode(node: PropertyInitializerEnterNode) {
            insidePropertyInitializer.push(true)
        }

        override fun visitPropertyInitializerExitNode(node: PropertyInitializerExitNode) {
            propertiesInClassInitializer.add(node.fir.symbol)
            insidePropertyInitializer.pop()
        }

        override fun visitInitBlockEnterNode(node: InitBlockEnterNode) {
            insideInitBlock.push(true)
        }

        override fun visitInitBlockExitNode(node: InitBlockExitNode) {
            insideInitBlock.pop()
        }

        override fun visitPostponedLambdaEnterNode(node: PostponedLambdaEnterNode) {
            insideLambdaOrAnonymous.push(true)
        }

        override fun visitPostponedLambdaExitNode(node: PostponedLambdaExitNode) {
            insideLambdaOrAnonymous.pop()
        }

        override fun visitAnonymousObjectEnterNode(node: AnonymousObjectEnterNode, data: Nothing?) {
            insideLambdaOrAnonymous.push(true)
        }

        override fun visitAnonymousObjectExpressionExitNode(node: AnonymousObjectExpressionExitNode, data: Nothing?) {
            insideLambdaOrAnonymous.popOrNull()
        }

        override fun visitVariableAssignmentNode(node: VariableAssignmentNode) {
            val symbol = getPropertySymbol(node) ?: return
            if (!symbol.isLocal) {
                propertiesInClassInitializer.add(symbol)
            }
            if (!symbol.fir.isVal || symbol !in properties) {
                return
            }

            val dataFlowVariableResult = node.getDataFlowVariable(dataFlowInfo)
            if (!dataFlowVariableResult.isFlowOnNodeExist) {
                return
            }
            val dataFlowVariable = dataFlowVariableResult.variable
            if (symbol.hasInitializer || !symbol.isLocal && !dataFlowVariable.hasThisReceiver()) {
                reporter.reportOn(node.fir.lValue.source, FirErrors.VAL_REASSIGNMENT, symbol, context)
                return
            }

            if (node.fir in capturedWrites) {
                val error = if (symbol.fir.isLocal) FirErrors.CAPTURED_VAL_INITIALIZATION else FirErrors.CAPTURED_MEMBER_VAL_INITIALIZATION
                reporter.reportOn(node.fir.lValue.source, error, symbol, context)
                return
            }

            val pathAwareInfo = propertyInitializationInfoData.getValue(node)
            for (label in pathAwareInfo.keys) {
                val info = pathAwareInfo.getValue(label)
                val kind = info[dataFlowVariable] ?: EventOccurrencesRange.ZERO
                if (kind.canBeRevisited()) {
                    reporter.reportOn(node.fir.lValue.source, FirErrors.VAL_REASSIGNMENT, symbol, context)
                    return
                }
            }
        }

        override fun visitQualifiedAccessNode(node: QualifiedAccessNode) {
            val symbol = getPropertySymbol(node) ?: return
            if (symbol.fir.isLateInit || symbol !in properties) return
            val dataFlowVariableResult = node.getDataFlowVariable(dataFlowInfo)
            if (!dataFlowVariableResult.isFlowOnNodeExist) {
                return
            }
            val dataFlowVariable = dataFlowVariableResult.variable
            if (symbol.isLocal) {
                if (symbol.hasInitializer || symbol.hasDelegate) return
            } else {
                if (insideLambdaOrAnonymous.topOrNull() == true || !dataFlowVariable.hasThisReceiver()) {
                    return
                }

                if (symbol.hasInitializer ||
                    symbol.backingFieldSymbol.let { it?.fir?.initializer != null } ||
                    symbol.hasDelegate
                ) {
                    if ((insideInitBlock.topOrNull() == true || insidePropertyInitializer.topOrNull() == true) &&
                        symbol !in propertiesInClassInitializer
                    ) {
                        reporter.reportOn(node.fir.source, FirErrors.UNINITIALIZED_VARIABLE, symbol, context)
                    }
                    return
                }
            }

            val pathAwareInfo = propertyInitializationInfoData.getValue(node)
            for ((edgeLabel, info) in pathAwareInfo.entries) {
                if (symbol.isLocal || edgeLabel !is UncaughtExceptionPath) {
                    val kind = info[dataFlowVariable] ?: EventOccurrencesRange.ZERO
                    if (!kind.isDefinitelyVisited()) {
                        reporter.reportOn(node.fir.source, FirErrors.UNINITIALIZED_VARIABLE, symbol, context)
                        return
                    }
                }
            }
        }

        private fun DataFlowVariable?.hasThisReceiver(): Boolean {
            if (this !is RealVariable) return false
            val dispatchReceiver = this.identifier.dispatchReceiver ?: return true
            return (dispatchReceiver as? RealVariable)?.isThisReference == true
        }

        private fun getPropertySymbol(node: CFGNode<*>): FirPropertySymbol? {
            return (node.fir as? FirQualifiedAccess)?.referredPropertySymbol
        }
    }
}
