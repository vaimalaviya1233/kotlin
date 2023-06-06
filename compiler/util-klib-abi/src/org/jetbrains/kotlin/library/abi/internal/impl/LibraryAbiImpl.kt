/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library.abi.internal.impl

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.library.abi.internal.*
import java.util.*

internal data class AbiSignaturesImpl(private val signatureV1: String?, private val signatureV2: String?) : AbiSignatures {
    override operator fun get(signatureVersion: AbiSignatureVersion): String? = when (signatureVersion) {
        AbiSignatureVersion.V1 -> signatureV1
        AbiSignatureVersion.V2 -> signatureV2
    }
}

internal class AbiTopLevelDeclarationsImpl(
    override val declarations: List<AbiDeclaration>
) : AbiTopLevelDeclarations

internal class AbiClassImpl(
    override val signatures: AbiSignatures,
    override val modality: Modality,
    override val kind: ClassKind,
    override val isInner: Boolean,
    override val isValue: Boolean,
    override val isFunction: Boolean,
    override val superTypes: SortedSet<AbiSuperType>,
    override val declarations: List<AbiDeclaration>
) : AbiClass

internal class AbiFunctionImpl(
    override val signatures: AbiSignatures,
    override val modality: Modality,
    override val isConstructor: Boolean,
    override val isInline: Boolean,
    override val valueParameterFlags: AbiFunction.ValueParameterFlags?
) : AbiFunction

internal class AbiPropertyImpl(
    override val signatures: AbiSignatures,
    override val modality: Modality,
    override val mutability: AbiProperty.Mutability
) : AbiProperty
