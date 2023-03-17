/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

@Suppress("DataClassPrivateConstructor")
data class Fir2IrResult private constructor(
    val irModuleFragment: IrModuleFragment,
    val components: Fir2IrComponents,
    val pluginContext: Fir2IrPluginContext,
    val removedExpectDeclarations: Set<FirDeclaration>
) {
    constructor(
        irModuleFragment: IrModuleFragment,
        components: Fir2IrComponents,
        moduleDescriptor: FirModuleDescriptor
    ) : this(irModuleFragment, components, Fir2IrPluginContext(components, moduleDescriptor), emptySet())
}
