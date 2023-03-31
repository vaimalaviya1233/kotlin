/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.actualizer

import org.jetbrains.kotlin.KtDiagnosticReporterWithImplicitIrBasedContext
import org.jetbrains.kotlin.backend.common.CommonBackendErrors
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isStatic
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.module
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.multiplatform.OptionalAnnotationUtil
import org.jetbrains.kotlin.utils.addToStdlib.runIf

fun Map<String, List<IrDeclaration>>.getMatch(
    expectDeclaration: IrDeclaration,
    expectActualTypesMap: Map<IrSymbol, IrSymbol>,
    expectActualTypeAliasMap: Map<FqName, FqName>
): IrDeclaration? {
    val regularMembers = this[generateIrElementFullNameFromExpect(expectDeclaration, expectActualTypeAliasMap)]
    if (regularMembers != null) {
        return if (expectDeclaration is IrFunction) {
            regularMembers.firstNotNullOfOrNull { runIf(expectDeclaration.match(it as IrFunction, expectActualTypesMap)) { it } }
        } else {
            regularMembers.singleOrNull()
        }
    }

    // hack to support matching expect companion members to actual static members. See KT-57661
    if (expectDeclaration is IrFunction && expectDeclaration.parent.let { it is IrClass && it.isCompanion }) {
        val name = generateIrElementFullNameFromExpect(expectDeclaration, expectActualTypeAliasMap, skipCompanion = true)
        val membersWithoutCompanion = this[name] ?: return null
        return membersWithoutCompanion.firstNotNullOfOrNull {
            runIf(expectDeclaration.match(it as IrFunction, expectActualTypesMap) && it.isStatic) { it }
        }
    }

    return null
}

private fun IrFunction.match(actualFunction: IrFunction, expectActualTypesMap: Map<IrSymbol, IrSymbol>): Boolean {
    fun getActualizedValueParameterSymbol(
        expectParameter: IrValueParameter,
        localTypeParametersMap: Map<IrTypeParameterSymbol, IrTypeParameterSymbol>? = null
    ): IrSymbol {
        return expectParameter.type.classifierOrFail.let {
            val localMappedSymbol = if (localTypeParametersMap != null && it is IrTypeParameterSymbol) {
                localTypeParametersMap[it]
            } else {
                null
            }
            localMappedSymbol ?: expectActualTypesMap[it] ?: it
        }
    }

    fun checkParameter(
        expectParameter: IrValueParameter?,
        actualParameter: IrValueParameter?,
        localTypeParametersMap: Map<IrTypeParameterSymbol, IrTypeParameterSymbol>
    ): Boolean {
        if (expectParameter == null) {
            return actualParameter == null
        }
        if (actualParameter == null) {
            return false
        }

        if (expectParameter.type is IrDynamicType || actualParameter.type is IrDynamicType) {
            return true
        }

        if (getActualizedValueParameterSymbol(expectParameter, localTypeParametersMap) !=
            getActualizedValueParameterSymbol(actualParameter)
        ) {
            return false
        }

        return true
    }

    if (valueParameters.size != actualFunction.valueParameters.size || typeParameters.size != actualFunction.typeParameters.size) {
        return false
    }

    val localTypeParametersMap = mutableMapOf<IrTypeParameterSymbol, IrTypeParameterSymbol>()
    for ((expectTypeParameter, actualTypeParameter) in typeParameters.zip(actualFunction.typeParameters)) {
        if (expectTypeParameter.name != actualTypeParameter.name) {
            return false
        }
        localTypeParametersMap[expectTypeParameter.symbol] = actualTypeParameter.symbol
    }

    if (!checkParameter(extensionReceiverParameter, actualFunction.extensionReceiverParameter, localTypeParametersMap)) {
        return false
    }

    for ((expectParameter, actualParameter) in valueParameters.zip(actualFunction.valueParameters)) {
        if (!checkParameter(expectParameter, actualParameter, localTypeParametersMap)) {
            return false
        }
    }

    return true
}

fun generateActualIrClassOrTypeAliasFullName(declaration: IrElement) = generateIrElementFullNameFromExpect(declaration, emptyMap())

fun generateIrElementFullNameFromExpect(
    declaration: IrElement,
    expectActualTypeAliasMap: Map<FqName, FqName>,
    skipCompanion: Boolean = false,
): String {
    return buildString { appendElementFullName(declaration, this, expectActualTypeAliasMap, skipCompanion) }
}

private fun appendElementFullName(
    declaration: IrElement,
    result: StringBuilder,
    expectActualTypeAliasMap: Map<FqName, FqName>,
    skipCompanion: Boolean = false,
) {
    if (declaration !is IrDeclarationBase) return

    val parents = mutableListOf<String>()
    var parent: IrDeclarationParent? = declaration.parent
    while (parent != null) {
        if (parent is IrDeclarationWithName) {
            val parentParent = parent.parent
            if (parentParent is IrClass) {
                if (!skipCompanion || !(parent is IrClass && parent.isCompanion)) {
                    parents.add(parent.name.asString())
                }
                parent = parentParent
                continue
            }
        }
        val parentString = parent.kotlinFqName.let { (expectActualTypeAliasMap[it] ?: it).asString() }
        if (parentString.isNotEmpty()) {
            parents.add(parentString)
        }
        parent = null
    }

    if (parents.isNotEmpty()) {
        result.append(parents.asReversed().joinToString(separator = "."))
        result.append('.')
    }

    if (declaration is IrDeclarationWithName) {
        result.append(declaration.name)
    }

    if (declaration is IrFunction) {
        result.append("()")
    }
}

@OptIn(ObsoleteDescriptorBasedAPI::class)
fun KtDiagnosticReporterWithImplicitIrBasedContext.reportMissingActual(irDeclaration: IrDeclaration) {
    at(irDeclaration).report(
        CommonBackendErrors.NO_ACTUAL_FOR_EXPECT,
        (irDeclaration as? IrDeclarationWithName)?.name?.asString().orEmpty(),
        irDeclaration.module
    )
}

internal fun IrElement.containsOptionalExpectation(): Boolean {
    return this is IrClass &&
            this.kind == ClassKind.ANNOTATION_CLASS &&
            this.hasAnnotation(OptionalAnnotationUtil.OPTIONAL_EXPECTATION_FQ_NAME)
}

