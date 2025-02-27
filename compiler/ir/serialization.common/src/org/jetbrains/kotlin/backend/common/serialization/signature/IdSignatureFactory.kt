/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.signature

import org.jetbrains.kotlin.backend.common.serialization.DeclarationTable
import org.jetbrains.kotlin.backend.common.serialization.mangle.MangleConstant
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.overrides.isOverridableFunction
import org.jetbrains.kotlin.ir.overrides.isOverridableProperty
import org.jetbrains.kotlin.ir.symbols.IrFileSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.ir.util.isFacadeClass
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

class PublicIdSignatureComputer(val mangler: KotlinMangler.IrMangler) : IdSignatureComputer {

    private val publicSignatureBuilder = PublicIdSigBuilder()

    override fun computeSignature(declaration: IrDeclaration): IdSignature {
        return publicSignatureBuilder.buildSignature(declaration)
    }

    fun composePublicIdSignature(declaration: IrDeclaration, compatibleMode: Boolean): IdSignature {
        assert(mangler.run { declaration.isExported(compatibleMode) }) {
            "${declaration.render()} expected to be exported"
        }

        return publicSignatureBuilder.buildSignature(declaration)
    }

    private var currentFileSignatureX: IdSignature.FileSignature? = null

    override fun inFile(file: IrFileSymbol?, block: () -> Unit) {
        currentFileSignatureX = file?.let { IdSignature.FileSignature(it) }

        block()

        currentFileSignatureX = null
    }

    private fun IrDeclaration.checkIfPlatformSpecificExport(): Boolean = mangler.run { isPlatformSpecificExport() }

    private var localCounter: Long = 0
    private var scopeCounter: Int = 0

    // TODO: we need to disentangle signature construction with declaration tables.
    lateinit var table: DeclarationTable

    fun reset() {
        localCounter = 0
        scopeCounter = 0
    }

    private inner class PublicIdSigBuilder : IdSignatureBuilder<IrDeclaration, KotlinMangler.IrMangler>(),
        IrElementVisitorVoid {

        override val mangler: KotlinMangler.IrMangler
            get() = this@PublicIdSignatureComputer.mangler

        override val currentFileSignature: IdSignature.FileSignature?
            get() = currentFileSignatureX

        override fun accept(d: IrDeclaration) {
            d.acceptVoid(this)
        }

        private fun collectParents(declaration: IrDeclarationWithName) {
            declaration.parent.acceptVoid(this)
            if (declaration !is IrClass || !declaration.isFacadeClass) {
                classFqnSegments.add(declaration.name.asString())
            }
        }

        override fun renderDeclarationForDescription(declaration: IrDeclaration): String = declaration.render()

        override fun visitElement(element: IrElement) =
            error("Unexpected element ${element.render()}")

        override fun visitErrorDeclaration(declaration: IrErrorDeclaration) {
            description = renderDeclarationForDescription(declaration)
        }

        override fun visitPackageFragment(declaration: IrPackageFragment) {
            packageFqn = declaration.packageFqName
        }

        private val IrDeclarationWithVisibility.isTopLevelPrivate: Boolean
            get() = visibility == DescriptorVisibilities.PRIVATE && !checkIfPlatformSpecificExport() &&
                    (parent is IrPackageFragment || parent.isFacadeClass)

        override fun visitClass(declaration: IrClass) {
            collectParents(declaration)
            isTopLevelPrivate = isTopLevelPrivate || declaration.isTopLevelPrivate
            if (declaration.kind == ClassKind.ENUM_ENTRY) {
                classFqnSegments.add(MangleConstant.ENUM_ENTRY_CLASS_NAME)
            }
            setDescriptionIfLocalDeclaration(declaration)
            setExpected(declaration.isExpect)
        }

        override fun visitSimpleFunction(declaration: IrSimpleFunction) {
            val property = declaration.correspondingPropertySymbol
            if (property != null) {
                property.owner.acceptVoid(this)
                if (container != null) {
                    createContainer()
                }
                setHashIdAndDescriptionFor(declaration, isPropertyAccessor = container == null)
                classFqnSegments.add(declaration.name.asString())
            } else {
                collectParents(declaration)
                isTopLevelPrivate = isTopLevelPrivate || declaration.isTopLevelPrivate
                setHashIdAndDescriptionFor(declaration, isPropertyAccessor = false)

                // If this is a local function, overwrite `description` with the IR function's rendered form.
                setDescriptionIfLocalDeclaration(declaration)
            }
            setExpected(declaration.isExpect)
        }

        override fun visitConstructor(declaration: IrConstructor) {
            collectParents(declaration)
            setHashIdAndDescriptionFor(declaration, isPropertyAccessor = false)
            setExpected(declaration.isExpect)
        }

        override fun visitScript(declaration: IrScript) {
            collectParents(declaration)
        }

        override fun visitProperty(declaration: IrProperty) {
            collectParents(declaration)
            isTopLevelPrivate = isTopLevelPrivate || declaration.isTopLevelPrivate
            setHashIdAndDescriptionFor(declaration, isPropertyAccessor = false)
            setExpected(declaration.isExpect)
        }

        override fun visitTypeAlias(declaration: IrTypeAlias) {
            collectParents(declaration)
            isTopLevelPrivate = isTopLevelPrivate || declaration.isTopLevelPrivate
        }

        override fun visitEnumEntry(declaration: IrEnumEntry) {
            collectParents(declaration)
        }

        override fun visitTypeParameter(declaration: IrTypeParameter) {
            val rawParent = declaration.parent

            val parent = if (rawParent is IrSimpleFunction) {
                rawParent.correspondingPropertySymbol?.owner ?: rawParent
            } else rawParent

            parent.accept(this, null)
            createContainer()

            if (parent is IrProperty && parent.setter == rawParent) {
                classFqnSegments.add(MangleConstant.TYPE_PARAMETER_MARKER_NAME_SETTER)
            } else {
                classFqnSegments.add(MangleConstant.TYPE_PARAMETER_MARKER_NAME)
            }
            setHashIdAndDescription(declaration.index.toLong(), renderDeclarationForDescription(declaration), isPropertyAccessor = false)
        }

        override fun visitField(declaration: IrField) {
            val prop = declaration.correspondingPropertySymbol?.owner

            if (prop != null) {
                // backing field
                prop.acceptVoid(this)
                createContainer()
                classFqnSegments.add(MangleConstant.BACKING_FIELD_NAME)
                setDescriptionIfLocalDeclaration(declaration)
            } else {
                collectParents(declaration)
                setHashIdAndDescriptionFor(declaration, isPropertyAccessor = false)
            }
        }
    }
}

/**
 * `IdSignatureSerializer` has been renamed to [IdSignatureFactory]. The [IdSignatureSerializer] type alias is added here
 * in place of the original `IdSignatureSerializer` class to keep source compatibility with the Compose compiler plugin
 * (this is only needed for a short duration of time).
 *
 * The [Deprecated] annotation was commented out because it leads to compilation errors in Compose plugin due to the use of `-Werror`.
 */
//@Deprecated(
//    message = "IdSignatureSerializer has been renamed to IdSignatureFactory." +
//            " Need to maintain this type alias for a while to keep source compatibility with the Compose plugin.",
//    replaceWith = ReplaceWith("IdSignatureFactory")
//)
@Suppress("unused")
typealias IdSignatureSerializer = IdSignatureFactory

class IdSignatureFactory(
    private val publicSignatureBuilder: PublicIdSignatureComputer,
    private val table: DeclarationTable,
    startIndex: Int
) : IdSignatureComputer {

    constructor(publicSignatureBuilder: PublicIdSignatureComputer, table: DeclarationTable) : this(publicSignatureBuilder, table, 0)

    private val mangler: KotlinMangler.IrMangler = publicSignatureBuilder.mangler

    override fun computeSignature(declaration: IrDeclaration): IdSignature {
        return publicSignatureBuilder.computeSignature(declaration)
    }

    fun composeSignatureForDeclaration(declaration: IrDeclaration, compatibleMode: Boolean): IdSignature {
        return if (mangler.run { declaration.isExported(compatibleMode) }) {
            publicSignatureBuilder.composePublicIdSignature(declaration, compatibleMode)
        } else composeFileLocalIdSignature(declaration, compatibleMode)
    }

    private var localIndex: Long = startIndex.toLong()
    private var scopeIndex: Int = startIndex

    override fun inFile(file: IrFileSymbol?, block: () -> Unit) {
        publicSignatureBuilder.inFile(file, block)
    }

    private fun composeContainerIdSignature(container: IrDeclarationParent, compatibleMode: Boolean): IdSignature =
        when (container) {
            is IrPackageFragment -> IdSignature.CommonSignature(
                packageFqName = container.packageFqName.asString(),
                declarationFqName = "",
                id = null,
                mask = 0,
                description = null,
            )
            is IrDeclaration -> table.signatureByDeclaration(container, compatibleMode)
            else -> error("Unexpected container ${container.render()}")
        }

    fun composeFileLocalIdSignature(declaration: IrDeclaration, compatibleMode: Boolean): IdSignature {
        assert(!mangler.run { declaration.isExported(compatibleMode) })

        return table.privateDeclarationSignature(declaration, compatibleMode) {
            when (declaration) {
                is IrValueDeclaration -> IdSignature.ScopeLocalDeclaration(scopeIndex++, declaration.name.asString())
                is IrAnonymousInitializer -> IdSignature.ScopeLocalDeclaration(scopeIndex++, "ANON INIT")
                is IrLocalDelegatedProperty -> IdSignature.ScopeLocalDeclaration(scopeIndex++, declaration.name.asString())
                is IrField -> {
                    val p = declaration.correspondingPropertySymbol?.let { composeSignatureForDeclaration(it.owner, compatibleMode) }
                        ?: composeContainerIdSignature(declaration.parent, compatibleMode)
                    IdSignature.FileLocalSignature(p, ++localIndex)
                }
                is IrSimpleFunction -> {
                    val parent = declaration.parent
                    val p = declaration.correspondingPropertySymbol?.let { composeSignatureForDeclaration(it.owner, compatibleMode) }
                        ?: composeContainerIdSignature(parent, compatibleMode)
                    IdSignature.FileLocalSignature(
                        p,
                        if (declaration.isOverridableFunction()) {
                            mangler.run { declaration.signatureMangle(compatibleMode) }
                        } else {
                            ++localIndex
                        },
                        declaration.render()
                    )
                }
                is IrProperty -> {
                    val parent = declaration.parent
                    IdSignature.FileLocalSignature(
                        composeContainerIdSignature(parent, compatibleMode),
                        if (declaration.isOverridableProperty()) {
                            mangler.run { declaration.signatureMangle(compatibleMode) }
                        } else {
                            ++localIndex
                        },
                        declaration.render()
                    )
                }
                else -> {
                    IdSignature.FileLocalSignature(
                        composeContainerIdSignature(declaration.parent, compatibleMode),
                        ++localIndex,
                        declaration.render()
                    )
                }
            }
        }
    }
}
