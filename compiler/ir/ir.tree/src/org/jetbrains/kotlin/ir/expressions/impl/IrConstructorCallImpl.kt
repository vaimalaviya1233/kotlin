/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.totalValueParametersCount

class IrConstructorCallImpl(
    override val startOffset: Int,
    override val endOffset: Int,
    override var type: IrType,
    override val symbol: IrConstructorSymbol,
    typeArgumentsCount: Int,
    override val constructorTypeArgumentsCount: Int,
    valueArgumentsCount: Int,
    override val origin: IrStatementOrigin? = null,
    override val source: SourceElement = SourceElement.NO_SOURCE,
    override var hasExtensionReceiver: Boolean = false,
    override var contextReceiversCount: Int = 0,
) : IrConstructorCall() {
    override val typeArgumentsByIndex: Array<IrType?> = arrayOfNulls(typeArgumentsCount)

    override val argumentsByParameterIndex: Array<IrExpression?> = arrayOfNulls(valueArgumentsCount)

    companion object {
        @ObsoleteDescriptorBasedAPI
        fun fromSymbolDescriptor(
            startOffset: Int,
            endOffset: Int,
            type: IrType,
            constructorSymbol: IrConstructorSymbol,
            origin: IrStatementOrigin? = null
        ): IrConstructorCallImpl {
            val constructorDescriptor = constructorSymbol.descriptor
            val classTypeParametersCount = constructorDescriptor.constructedClass.original.declaredTypeParameters.size
            val totalTypeParametersCount = constructorDescriptor.typeParameters.size
            val valueParametersCount =
                constructorDescriptor.valueParameters.size
                    .totalValueParametersCount(constructorDescriptor.contextReceiverParameters.size, hasExtensionReceiver = false)
            return IrConstructorCallImpl(
                startOffset, endOffset,
                type,
                constructorSymbol,
                typeArgumentsCount = totalTypeParametersCount,
                constructorTypeArgumentsCount = totalTypeParametersCount - classTypeParametersCount,
                valueArgumentsCount = valueParametersCount,
                contextReceiversCount = constructorDescriptor.contextReceiverParameters.size,
                origin = origin
            )
        }

        fun fromSymbolOwner(
            startOffset: Int,
            endOffset: Int,
            type: IrType,
            constructorSymbol: IrConstructorSymbol,
            classTypeParametersCount: Int,
            origin: IrStatementOrigin? = null
        ): IrConstructorCallImpl {
            val constructor = constructorSymbol.owner
            val constructorTypeParametersCount = constructor.typeParameters.size
            val totalTypeParametersCount = classTypeParametersCount + constructorTypeParametersCount
            val valueParametersCount = constructor.valueParameters.size

            return IrConstructorCallImpl(
                startOffset, endOffset,
                type,
                constructorSymbol,
                totalTypeParametersCount,
                constructorTypeParametersCount,
                valueParametersCount,
                origin
            )
        }

        fun fromSymbolOwner(
            startOffset: Int,
            endOffset: Int,
            type: IrType,
            constructorSymbol: IrConstructorSymbol,
            origin: IrStatementOrigin? = null
        ): IrConstructorCallImpl {
            val constructedClass = constructorSymbol.owner.parentAsClass
            val classTypeParametersCount = constructedClass.typeParameters.size
            return fromSymbolOwner(startOffset, endOffset, type, constructorSymbol, classTypeParametersCount, origin)
        }

        fun fromSymbolOwner(
            type: IrType,
            constructorSymbol: IrConstructorSymbol,
            origin: IrStatementOrigin? = null
        ): IrConstructorCallImpl =
            fromSymbolOwner(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, constructorSymbol, constructorSymbol.owner.parentAsClass.typeParameters.size,
                origin
            )

        fun createCopy(
            original: IrConstructorCall,
            startOffset: Int = original.startOffset,
            endOffset: Int = original.endOffset,
            type: IrType = original.type,
            symbol: IrConstructorSymbol = original.symbol,
            typeArgumentsCount: Int = original.typeArgumentsCount,
            constructorTypeArgumentsCount: Int = original.typeArgumentsCount,
            valueArgumentsCount: Int = original.valueArgumentsCount,
            origin: IrStatementOrigin? = original.origin,
            source: SourceElement = original.source,
            hasExtensionReceiver: Boolean = original.hasExtensionReceiver,
            contextReceiversCount: Int = original.contextReceiversCount,
        ) = IrConstructorCallImpl(
            startOffset, endOffset, type, symbol, typeArgumentsCount, constructorTypeArgumentsCount, valueArgumentsCount, origin, source,
            hasExtensionReceiver, contextReceiversCount
        )
    }
}
