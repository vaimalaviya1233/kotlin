/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.generator.print

import com.squareup.kotlinpoet.*
import org.jetbrains.kotlin.ir.generator.BASE_PACKAGE
import org.jetbrains.kotlin.ir.generator.config.UseFieldAsParameterInIrFactoryStrategy
import org.jetbrains.kotlin.ir.generator.model.Model
import org.jetbrains.kotlin.ir.generator.util.GeneratedFile
import org.jetbrains.kotlin.ir.generator.util.parameterizedByIfAny
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import java.io.File

private val factoryTypeName = ClassName("$BASE_PACKAGE.declarations", "IrFactory")

internal fun printFactory(generationPath: File, model: Model): GeneratedFile {
    val visitorType = TypeSpec.interfaceBuilder(factoryTypeName).apply {
        addProperty("stageController", ClassName(factoryTypeName.packageName, "StageController"))
        model.elements
            .filter { it.isLeaf && it.generateIrFactoryMethod }
            .sortedWith(compareBy({ it.packageName }, { it.name }))
            .forEach { element ->
                val typeParams = element.params.map { it.toPoet() }
                addFunction(
                    FunSpec.builder("create${element.name.capitalizeAsciiOnly()}")
                        .addTypeVariables(typeParams)
                        .apply {
                            val fields = (element.allFieldsRecursively() + element.additionalFactoryMethodParameters)
                                .filterNot { it.name in element.fieldsToSkipInIrFactoryMethod }
                                .mapNotNull { field ->
                                    (field.useInIrFactoryStrategy as? UseFieldAsParameterInIrFactoryStrategy.Yes)?.let {
                                        field to it.defaultValue
                                    }
                                }
                                .sortedBy { (_, defaultValue) -> defaultValue != null } // All parameters with default values must go last
                            fields.forEach { (field, defaultValue) ->
                                addParameter(
                                    ParameterSpec.builder(field.name, field.type.toPoet().copy(nullable = field.nullable))
                                        .defaultValue(defaultValue)
                                        .build(),
                                )
                            }
                        }
                        .returns(element.toPoet().parameterizedByIfAny(typeParams))
                        .addModifiers(KModifier.ABSTRACT)
                        .build(),
                )
            }
    }.build()

    return printTypeCommon(generationPath, factoryTypeName.packageName, visitorType)
}