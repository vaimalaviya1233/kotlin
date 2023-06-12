/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.jvm.checkers

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.checkers.ExpectedActualDeclarationChecker.Companion.isCompatibleOrWeakCompatible
import org.jetbrains.kotlin.resolve.checkers.OptInUsageChecker
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.multiplatform.ExpectedActualResolver

private val unsafeJvmImplicitActualizationFqn = FqName("kotlin.jvm.UnsafeJvmImplicitActualization")

object JavaImplicitActualizationChecker : DeclarationChecker {
    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        val settings = context.languageVersionSettings
        if (!settings.supportsFeature(LanguageFeature.MultiPlatformProjects)) return
        if (declaration !is KtNamedDeclaration) return
        if (descriptor !is MemberDescriptor) return
        if (!descriptor.isExpect) return

        val module = descriptor.module
        val actuals = ExpectedActualResolver.findActualForExpected(descriptor, module)
            ?.filter { (compatibility, _) -> compatibility.isCompatibleOrWeakCompatible() }
            ?.flatMap { (_, members) -> members }
            ?.takeIf(List<MemberDescriptor>::isNotEmpty)
            ?: return

        if (actuals.any {
                it is JavaClassDescriptor &&
                        it.containingDeclaration is PackageFragmentDescriptor &&
                        with(OptInUsageChecker) {
                            !declaration.isOptInAllowed(unsafeJvmImplicitActualizationFqn, settings, context.trace.bindingContext)
                        }
            }
        ) {
            context.trace.report(Errors.IMPLICIT_JVM_ACTUALIZATION.on(declaration, descriptor, module))
        }
    }
}
