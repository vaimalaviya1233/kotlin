/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import com.sun.tools.javac.tree.JCTree
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.utils.addToStdlib.runIf
import org.jetbrains.kotlin.utils.addToStdlib.zipWithDefault

private typealias SubstitutionMap = Map<String, Pair<KtTypeParameter, KtTypeProjection>>

context(Kapt4ContextForStubGeneration)
class ErrorTypeCorrector(
    private val converter: Kapt4StubGenerator,
    private val typeKind: TypeKind,
    file: KtFile
) {
    private val defaultType = treeMaker.FqName(Object::class.java.name)

    private val aliasedImports = mutableMapOf<String, JCTree.JCExpression>().apply {
        for (importDirective in file.importDirectives) {
            if (importDirective.isAllUnder) continue

            val aliasName = importDirective.aliasName ?: continue
            val importedFqName = importDirective.importedFqName ?: continue
// TODO
//            val importedReference = getReferenceExpression(importDirective.importedReference)
//                ?.let { bindingContext[BindingContext.REFERENCE_TARGET, it] }
//
//            if (importedReference is CallableDescriptor) continue
//
//            this[aliasName] = treeMaker.FqName(importedFqName)
        }
    }

    enum class TypeKind {
        RETURN_TYPE, METHOD_PARAMETER_TYPE, SUPER_TYPE, ANNOTATION
    }

    fun convert(
        psiType: PsiType?,
        ktType: KtTypeElement?
    ): JCTree.JCExpression {
        return when (ktType) {
            is KtUserType -> convertUserType(psiType, ktType)
            is KtNullableType -> convert(psiType, ktType.innerType ?: return defaultType)
            is KtFunctionType -> TODO() // convertFunctionType(ktType, substitutions)
            null -> if (psiType == null) defaultType else treeMaker.TypeWithArguments(psiType)
            else -> defaultType
        }
    }

    private fun convertUserType(
        psiType: PsiType?,
        ktType: KtUserType
    ): JCTree.JCExpression {
        val typeName = psiType?.qualifiedNameOrNull ?: ktType.referencedName ?: NO_NAME_PROVIDED
        val baseJType = treeMaker.SimpleName(typeName)

        val psiArguments = when (psiType) {
            is PsiClassType -> psiType.parameters.asList()
            is PsiArrayType -> listOf(psiType.componentType)
            else -> emptyList()
        }

        val ktArguments = ktType.typeArgumentsAsTypes

        if (psiArguments.isEmpty() && ktArguments.isEmpty()) return baseJType

        val jArguments = psiArguments.zipWithDefault(
            ktArguments,
            { null },
            { null }
        ).mapJList { (psiArgument, ktArgumentReference) ->
            convert(psiArgument, ktArgumentReference?.typeElement)
        }

        return treeMaker.TypeApply(baseJType, jArguments)
    }

    private fun convertTypeProjection(
        projection: KtTypeProjection,
        variance: Variance?,
        substitutions: SubstitutionMap
    ): JCTree.JCExpression {
        TODO()
//        fun unbounded() = treeMaker.Wildcard(treeMaker.TypeBoundKind(BoundKind.UNBOUND), null)
//
//        // Use unbounded wildcard when a generic argument can't be resolved
//        val argumentType = projection.typeReference ?: return unbounded()
//        val argumentExpression by lazy { convert(argumentType, substitutions) }
//
//        if (variance === Variance.INVARIANT) {
//            return argumentExpression
//        }
//
//        val projectionKind = projection.projectionKind
//
//        return when {
//            projectionKind === KtProjectionKind.STAR -> treeMaker.Wildcard(treeMaker.TypeBoundKind(BoundKind.UNBOUND), null)
//            projectionKind === KtProjectionKind.IN || variance === Variance.IN_VARIANCE ->
//                treeMaker.Wildcard(treeMaker.TypeBoundKind(BoundKind.SUPER), argumentExpression)
//
//            projectionKind === KtProjectionKind.OUT || variance === Variance.OUT_VARIANCE ->
//                treeMaker.Wildcard(treeMaker.TypeBoundKind(BoundKind.EXTENDS), argumentExpression)
//
//            else -> argumentExpression // invariant
//        }
    }

    private fun convertFunctionType(type: KtFunctionType, substitutions: SubstitutionMap): JCTree.JCExpression {
        TODO()
//        val receiverType = type.receiverTypeReference
//        var parameterTypes = mapJList(type.parameters) { convert(it.typeReference, substitutions) }
//        val returnType = convert(type.returnTypeReference, substitutions)
//
//        if (receiverType != null) {
//            parameterTypes = parameterTypes.prepend(convert(receiverType, substitutions))
//        }
//
//        parameterTypes = parameterTypes.append(returnType)
//
//        val treeMaker = converter.treeMaker
//        return treeMaker.TypeApply(treeMaker.SimpleName("Function" + (parameterTypes.size - 1)), parameterTypes)
    }

    private fun KtTypeParameterListOwner.getSubstitutions(actualType: KtUserType): SubstitutionMap {
        TODO()
//        val arguments = actualType.typeArguments
//
//        if (typeParameters.size != arguments.size) {
//            val kaptContext = converter.kaptContext
//            val error = kaptContext.kaptError("${typeParameters.size} parameters are expected but ${arguments.size} passed")
//            kaptContext.compiler.log.report(error)
//            return emptyMap()
//        }
//
//        val substitutionMap = mutableMapOf<String, Pair<KtTypeParameter, KtTypeProjection>>()
//
//        typeParameters.forEachIndexed { index, typeParameter ->
//            val name = typeParameter.name ?: return@forEachIndexed
//            substitutionMap[name] = Pair(typeParameter, arguments[index])
//        }
//
//        return substitutionMap
    }
}
