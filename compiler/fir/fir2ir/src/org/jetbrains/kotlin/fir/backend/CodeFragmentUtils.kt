/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.fir.declarations.FirCodeFragment
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

class CodeFragmentConversionData(
    val classId: ClassId,
    val methodName: Name,
    val capturedSymbols: List<CodeFragmentCapturedSymbol>
)

class CodeFragmentCapturedSymbol(val symbol: FirBasedSymbol<*>, val isMutated: Boolean)

private object CodeFragmentTowerDataContext : FirDeclarationDataKey()

private var FirCodeFragment.conversionDataOpt: CodeFragmentConversionData? by FirDeclarationDataRegistry.data(CodeFragmentTowerDataContext)

var FirCodeFragment.conversionData: CodeFragmentConversionData
    get() = conversionDataOpt ?: error("Conversion data is not set")
    set(newValue) {
        conversionDataOpt = newValue
    }