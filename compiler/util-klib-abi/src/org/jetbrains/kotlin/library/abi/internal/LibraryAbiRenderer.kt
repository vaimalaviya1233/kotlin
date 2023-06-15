/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library.abi.internal

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import java.lang.Appendable

/**
 * The default rendering implementation.
 */
fun AbiTopLevelDeclarations.renderTopLevels(settings: AbiRenderingSettings): String = buildString {
    renderTopLevelsTo(this, settings)
}

fun AbiTopLevelDeclarations.renderTopLevelsTo(output: Appendable, settings: AbiRenderingSettings) {
    settings.renderingOrder.renderWithSpecificOrder(
        this,
        { it.renderClassTo(output, settings, 0u) },
        { it.renderFunctionTo(output, settings, 0u) },
        { it.renderPropertyTo(output, settings, 0u) }
    )
}

fun AbiClass.renderClassTo(output: Appendable, settings: AbiRenderingSettings, indent: UInt): Unit = renderCommonDeclarationTo(
    output,
    settings,
    indent,
    doBeforeSignatures = {
        if (isInner) output.append("inner ")
        if (isValue) output.append("value ")
        if (isFunction) output.append("fun ")
        output.appendClassKind(kind)
    },
    doAfterSignatures = {
        if (superTypes.isNotEmpty()) {
            output.append(" : ")
            superTypes.joinTo(output, separator = ", ")
        }

        if (declarations.isNotEmpty()) {
            output.appendLine(" {")
            val nextIndent = indent + 1u
            settings.renderingOrder.renderWithSpecificOrder(
                this,
                { it.renderClassTo(output, settings, nextIndent) },
                { it.renderFunctionTo(output, settings, nextIndent) },
                { it.renderPropertyTo(output, settings, nextIndent) }
            )
            output.appendIndent(indent).append('}')
        }
    }
)

fun AbiFunction.renderFunctionTo(output: Appendable, settings: AbiRenderingSettings, indent: UInt) = renderCommonDeclarationTo(
    output,
    settings,
    indent,
    doBeforeSignatures = {
        if (isInline) output.append("inline ")
        output.append(if (isConstructor) "constructor" else "fun")
        output.appendValueParameterFlags(valueParameterFlags)
    }
)

fun AbiProperty.renderPropertyTo(output: Appendable, settings: AbiRenderingSettings, indent: UInt) = renderCommonDeclarationTo(
    output,
    settings,
    indent,
    doBeforeSignatures = {
        output.appendMutability(mutability)
    }
)

inline fun <T : AbiDeclaration> T.renderCommonDeclarationTo(
    output: Appendable,
    settings: AbiRenderingSettings,
    indent: UInt,
    doBeforeSignatures: T.() -> Unit,
    doAfterSignatures: T.() -> Unit = {},
) {
    output.appendIndent(indent).appendModality(modality).append(' ')
    doBeforeSignatures()
    output.append(' ').appendSignatures(this, settings)
    doAfterSignatures()
    output.appendLine()
}

fun Appendable.appendIndent(indent: UInt): Appendable {
    for (i in 0u until indent) append("    ")
    return this
}

fun Appendable.appendModality(modality: Modality): Appendable = append(
    when (modality) {
        Modality.SEALED -> "sealed"
        Modality.ABSTRACT -> "abstract"
        Modality.OPEN -> "open"
        Modality.FINAL -> "final"
    }
)

fun Appendable.appendClassKind(classKind: ClassKind): Appendable = append(
    classKind.codeRepresentation ?: if (classKind == ClassKind.ENUM_ENTRY) "enum entry" else error("Unexpected class kind: $classKind")
)

fun Appendable.appendMutability(mutability: AbiProperty.Mutability): Appendable = append(
    when (mutability) {
        AbiProperty.Mutability.VAL -> "val"
        AbiProperty.Mutability.CONST_VAL -> "const val"
        AbiProperty.Mutability.VAR -> "var"
    }
)

fun Appendable.appendValueParameterFlags(valueParameterFlags: AbiFunction.ValueParameterFlags?): Appendable {
    if (valueParameterFlags != null) {
        append('[')
        valueParameterFlags.flags.entries.joinTo(this, separator = " ") { (index, flags) ->
            "$index:" + flags.joinToString(separator = ",") { flag ->
                when (flag!!) {
                    AbiFunction.ValueParameterFlag.HAS_DEFAULT_ARG -> "default_arg"
                    AbiFunction.ValueParameterFlag.NOINLINE -> "noinline"
                    AbiFunction.ValueParameterFlag.CROSSINLINE -> "crossinline"
                }
            }
        }
        append(']')
    }
    return this
}

fun Appendable.appendSignatures(declaration: AbiDeclaration, settings: AbiRenderingSettings): Appendable =
    settings.renderedSignatureVersions.joinTo(this, separator = ", ") { signatureVersion ->
        declaration.signatures[signatureVersion] ?: error("No signature $signatureVersion for ${declaration::class.java}, $declaration")
    }

/**
 * @property renderingOrder The order in which member declarations are rendered.
 *
 * @param renderedSignatureVersions One might want to render only some signatures, e.g. only [AbiSignatureVersion.V2] even if
 * [AbiSignatureVersion.V1] are available.
 */
class AbiRenderingSettings(
    renderedSignatureVersions: Set<AbiSignatureVersion>,
    val renderingOrder: AbiRenderingOrder = AbiRenderingOrder.Default
) {
    init {
        require(renderedSignatureVersions.isNotEmpty())
    }

    val renderedSignatureVersions: List<AbiSignatureVersion> =
        renderedSignatureVersions.sortedDescending() // The latest version always goes first.
}

interface AbiRenderingOrder {
    fun renderWithSpecificOrder(
        container: AbiDeclarationsContainer,
        renderClass: (AbiClass) -> Unit,
        renderFunction: (AbiFunction) -> Unit,
        renderProperty: (AbiProperty) -> Unit,
    )

    object Default : AbiRenderingOrder {
        override fun renderWithSpecificOrder(
            container: AbiDeclarationsContainer,
            renderClass: (AbiClass) -> Unit,
            renderFunction: (AbiFunction) -> Unit,
            renderProperty: (AbiProperty) -> Unit,
        ) {
            // Just follow the existing order.
            container.declarations.forEach { declaration ->
                when (declaration) {
                    is AbiClass -> renderClass(declaration)
                    is AbiFunction -> renderFunction(declaration)
                    is AbiProperty -> renderProperty(declaration)
                }
            }
        }
    }
}
