/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library.abi.internal

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import java.util.*

/**
 * @property manifestInfo Information from manifest that might be useful.
 * @property supportedSignatureVersions The versions of signatures supported by the given KLIB.
 * @property topLevelDeclarations Top-level declarations.
 */
class LibraryAbi(
    val manifestInfo: LibraryManifestInfo,
    val supportedSignatureVersions: Set<AbiSignatureVersion>,
    val topLevelDeclarations: AbiTopLevelDeclarations
)

enum class AbiSignatureVersion {
    /**
     *  The signatures with hashes.
     */
    V1,

    /**
     * The self-descriptive signatures.
     */
    V2,
}

interface AbiSignatures {
    /** Returns the signature of the specified [AbiSignatureVersion] **/
    operator fun get(signatureVersion: AbiSignatureVersion): String?
}

/**
 * Important: The order of [declarations] is preserved exactly as in serialized IR.
 * Would you need to use a different order while rendering, please refer to [AbiRenderingSettings.renderingOrder].
 */
interface AbiDeclarationsContainer {
    val declarations: List<AbiDeclaration>
}

interface AbiTopLevelDeclarations : AbiDeclarationsContainer

interface AbiDeclaration {
    val signatures: AbiSignatures
    val modality: Modality
}

/**
 * [superTypes] - the set of non-trivial supertypes (i.e. excluding [kotlin/Any] for regular classes, [kotlin/Enum] for enums, etc).
 */
interface AbiClass : AbiDeclaration, AbiDeclarationsContainer {
    val kind: ClassKind
    val isInner: Boolean
    val isValue: Boolean
    val isFunction: Boolean
    val superTypes: SortedSet<AbiSuperType>
}

// TODO: decide how to render type arguments (effective(?) variance and effective(?) upper-bounds)
typealias AbiSuperType = String

/**
 * [valueParameterFlags] additional value parameter flags that might affect binary compatibility and that should be rendered along with
 *   the function itself
 */
interface AbiFunction : AbiDeclaration {
    val isConstructor: Boolean
    val isInline: Boolean
    val valueParameterFlags: ValueParameterFlags?

    enum class ValueParameterFlag { HAS_DEFAULT_ARG, NOINLINE, CROSSINLINE }

    /** [flags] is a map where key [Int] is a value parameter index (starting from 0)  */
    data class ValueParameterFlags(val flags: SortedMap<Int, SortedSet<ValueParameterFlag>>)
}

interface AbiProperty : AbiDeclaration {
    val mutability: Mutability

    enum class Mutability { VAL, CONST_VAL, VAR }
}
