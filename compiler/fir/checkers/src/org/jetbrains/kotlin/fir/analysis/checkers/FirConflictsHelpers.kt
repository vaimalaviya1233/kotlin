/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirNameConflictsTracker
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl.Companion.DEFAULT_STATUS_FOR_STATUSLESS_DECLARATIONS
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl.Companion.DEFAULT_STATUS_FOR_SUSPEND_MAIN_FUNCTION
import org.jetbrains.kotlin.fir.declarations.impl.modifiersRepresentation
import org.jetbrains.kotlin.fir.declarations.utils.nameOrSpecialName
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.outerType
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.scope
import org.jetbrains.kotlin.fir.scopes.CallableCopyTypeCalculator
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.impl.FirPackageMemberScope
import org.jetbrains.kotlin.fir.scopes.impl.TypeAliasConstructorsSubstitutingScope
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.util.ListMultimap
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.utils.SmartSet

val DEFAULT_STATUS_FOR_NORMAL_MAIN_FUNCTION = DEFAULT_STATUS_FOR_STATUSLESS_DECLARATIONS

private val FirNamedFunctionSymbol.hasMainFunctionStatus
    get() = when (resolvedStatus.modifiersRepresentation) {
        DEFAULT_STATUS_FOR_NORMAL_MAIN_FUNCTION.modifiersRepresentation,
        DEFAULT_STATUS_FOR_SUSPEND_MAIN_FUNCTION.modifiersRepresentation,
        -> true
        else -> false
    }

private val CallableId.isTopLevel get() = className == null

private fun FirBasedSymbol<*>.isCollectable(): Boolean {
    if (this is FirCallableSymbol<*>) {
        if (resolvedContextReceivers.any { it.typeRef.coneType.hasError() }) return false
        if (typeParameterSymbols.any { it.toConeType().hasError() }) return false
        if (receiverParameter?.typeRef?.coneType?.hasError() == true) return false
        if (this is FirFunctionSymbol<*> && valueParameterSymbols.any { it.resolvedReturnType.hasError() }) return false
    }

    return when (this) {
        // - see tests with `fun () {}`.
        // you can't redeclare something that has no name.
        is FirNamedFunctionSymbol -> source?.kind !is KtFakeSourceElementKind && name != SpecialNames.NO_NAME_PROVIDED
        is FirRegularClassSymbol -> name != SpecialNames.NO_NAME_PROVIDED
        // - see testEnumValuesValueOf.
        // it generates a static function that has
        // the same signature as the function defined
        // explicitly.
        is FirPropertySymbol -> source?.kind !is KtFakeSourceElementKind.EnumGeneratedDeclaration
        // class delegation field will be renamed after by the IR backend in a case of a name clash
        is FirFieldSymbol -> source?.kind != KtFakeSourceElementKind.ClassDelegationField
        else -> true
    }
}

private val FirBasedSymbol<*>.resolvedStatus
    get() = when (this) {
        is FirCallableSymbol<*> -> resolvedStatus
        is FirClassLikeSymbol<*> -> resolvedStatus
        else -> null
    }

private fun isExpectAndActual(declaration1: FirBasedSymbol<*>, declaration2: FirBasedSymbol<*>): Boolean {
    val status1 = declaration1.resolvedStatus ?: return false
    val status2 = declaration2.resolvedStatus ?: return false
    return (status1.isExpect && status2.isActual) || (status1.isActual && status2.isExpect)
}

private class DeclarationBuckets {
    val simpleFunctions = mutableListOf<Pair<FirNamedFunctionSymbol, String>>()
    val constructors = mutableListOf<Pair<FirConstructorSymbol, String>>()
    val classLikes = mutableListOf<Pair<FirClassLikeSymbol<*>, String>>()
    val properties = mutableListOf<Pair<FirPropertySymbol, String>>()
    val extensionProperties = mutableListOf<Pair<FirPropertySymbol, String>>()
}

private fun groupTopLevelByName(declarations: List<FirDeclaration>, context: CheckerContext): Map<Name, DeclarationBuckets> {
    val groups = mutableMapOf<Name, DeclarationBuckets>()
    for (declaration in declarations) {
        if (!declaration.symbol.isCollectable()) continue

        when (declaration) {
            is FirSimpleFunction ->
                groups.getOrPut(declaration.name, ::DeclarationBuckets).simpleFunctions +=
                    declaration.symbol to FirRedeclarationPresenter.represent(declaration.symbol)
            is FirProperty -> {
                val group = groups.getOrPut(declaration.name, ::DeclarationBuckets)
                val representation = FirRedeclarationPresenter.represent(declaration.symbol)
                if (declaration.receiverParameter != null) {
                    group.extensionProperties += declaration.symbol to representation
                } else {
                    group.properties += declaration.symbol to representation
                }
            }
            is FirClassLikeDeclaration -> {
                val representation = FirRedeclarationPresenter.represent(declaration.symbol) ?: continue
                val group = groups.getOrPut(declaration.nameOrSpecialName, ::DeclarationBuckets)
                group.classLikes += declaration.symbol to representation

                declaration.symbol.expandedClassWithConstructorsScope(context)?.let { (expandedClass, scopeWithConstructors) ->
                    if (expandedClass.classKind == ClassKind.OBJECT) {
                        return@let
                    }

                    scopeWithConstructors.processDeclaredConstructors {
                        group.constructors += it to FirRedeclarationPresenter.represent(it, declaration.symbol)
                    }
                }
            }
            else -> {}
        }
    }
    return groups
}

/**
 * Collects symbols of FirDeclarations for further analysis.
 */
class FirDeclarationCollector<D : FirBasedSymbol<*>>(
    internal val context: CheckerContext,
) {
    internal val session: FirSession get() = context.sessionHolder.session

    val declarationConflictingSymbols: HashMap<D, SmartSet<FirBasedSymbol<*>>> = hashMapOf()
}

fun FirDeclarationCollector<FirBasedSymbol<*>>.collectClassMembers(klass: FirRegularClassSymbol) {
    val otherDeclarations = mutableMapOf<String, MutableList<FirBasedSymbol<*>>>()
    val functionDeclarations = mutableMapOf<String, MutableList<FirBasedSymbol<*>>>()

    // TODO, KT-61243: Use declaredMemberScope
    @OptIn(SymbolInternals::class)
    for (it in klass.fir.declarations) {
        if (!it.symbol.isCollectable()) continue

        when (it) {
            is FirSimpleFunction -> collect(it.symbol, FirRedeclarationPresenter.represent(it.symbol), functionDeclarations)
            is FirRegularClass -> {
                collect(it.symbol, FirRedeclarationPresenter.represent(it.symbol), otherDeclarations)

                // Objects have implicit FirPrimaryConstructors
                if (it.symbol.classKind == ClassKind.OBJECT) {
                    continue
                }

                it.symbol.expandedClassWithConstructorsScope(context)?.let { (_, scopeWithConstructors) ->
                    scopeWithConstructors.processDeclaredConstructors { constructor ->
                        collect(constructor, FirRedeclarationPresenter.represent(constructor, it.symbol), functionDeclarations)
                    }
                }
            }
            is FirTypeAlias -> collect(it.symbol, FirRedeclarationPresenter.represent(it.symbol), otherDeclarations)
            is FirVariable -> collect(it.symbol, FirRedeclarationPresenter.represent(it.symbol), otherDeclarations)
            else -> {}
        }
    }
}

fun collectConflictingLocalFunctionsFrom(block: FirBlock, context: CheckerContext): Map<FirFunctionSymbol<*>, Set<FirBasedSymbol<*>>> {
    val collectables =
        block.statements.filter {
            (it is FirSimpleFunction || it is FirRegularClass) && (it as FirDeclaration).symbol.isCollectable()
        }

    if (collectables.isEmpty()) return emptyMap()

    val inspector = FirDeclarationCollector<FirFunctionSymbol<*>>(context)
    val functionDeclarations = mutableMapOf<String, MutableList<FirFunctionSymbol<*>>>()

    for (collectable in collectables) {
        when (collectable) {
            is FirSimpleFunction ->
                inspector.collect(collectable.symbol, FirRedeclarationPresenter.represent(collectable.symbol), functionDeclarations)
            is FirClassLikeDeclaration -> {
                collectable.symbol.expandedClassWithConstructorsScope(context)?.let { (_, scopeWithConstructors) ->
                    scopeWithConstructors.processDeclaredConstructors {
                        inspector.collect(it, FirRedeclarationPresenter.represent(it, collectable.symbol), functionDeclarations)
                    }
                }
            }
            else -> {}
        }
    }

    return inspector.declarationConflictingSymbols
}

private fun <D : FirBasedSymbol<*>> FirDeclarationCollector<D>.collect(
    declaration: D,
    representation: String,
    map: MutableMap<String, MutableList<D>>,
) {
    map.getOrPut(representation, ::mutableListOf).also {
        it.add(declaration)

        val conflicts = SmartSet.create<FirBasedSymbol<*>>()
        for (otherDeclaration in it) {
            if (otherDeclaration != declaration && !isOverloadable(declaration, otherDeclaration, session)) {
                conflicts.add(otherDeclaration)
                declarationConflictingSymbols.getOrPut(otherDeclaration) { SmartSet.create() }.add(declaration)
            }
        }

        declarationConflictingSymbols[declaration] = conflicts
    }
}

/**
 * To check top-level declarations for redeclarations, we check multiple sources (the packageMemberScope's properties, functions
 * and classifiers), redeclared classifiers from session.nameConflictsTracker and the file's declarations themselves.
 * To prevent inspecting the same source multiple times, we group the declarations in the file by name and subdivide them into
 * buckets (the properties of DeclarationGroup).
 *
 * Depending on the presence of declarations in the buckets, some checks can be omitted.
 * E.g., if there are no functions and no classes with constructors in the file, we don't need to inspect functions.
 *
 * #### Matrix of possible conflicts between "sources" and "buckets"
 *
 * |                         | simpleFunctions | constructors | classLikes | Properties | extensionProperties |
 * |-------------------------|-----------------|--------------|------------|------------|---------------------|
 * | functions               | X               | X            |            |            |                     |
 * | classifiers             |                 |              | X          | X          |                     |
 * | constructors of classes | X               |              |            |            |                     |
 * | properties              |                 |              | X          | X          | X                   |
 */
@Suppress("GrazieInspection")
fun FirDeclarationCollector<FirBasedSymbol<*>>.collectTopLevel(file: FirFile, packageMemberScope: FirPackageMemberScope) {

    for ((declarationName, group) in groupTopLevelByName(file.declarations, context)) {
        val groupHasClassLikesOrProperties = group.classLikes.isNotEmpty() || group.properties.isNotEmpty()
        val groupHasSimpleFunctions = group.simpleFunctions.isNotEmpty()

        fun collect(
            declarations: List<Pair<FirBasedSymbol<*>, String>>,
            conflictingSymbol: FirBasedSymbol<*>,
            conflictingPresentation: String? = null,
            conflictingFile: FirFile? = null,
        ) {
            for ((declaration, declarationPresentation) in declarations) {
                collectTopLevelConflict(
                    declaration,
                    declarationPresentation,
                    file,
                    conflictingSymbol,
                    conflictingPresentation,
                    conflictingFile
                )

                session.lookupTracker?.recordLookup(declarationName, file.packageFqName.asString(), declaration.source, file.source)
            }
        }

        fun collectFromClassifierSource(
            conflictingSymbol: FirClassifierSymbol<*>,
            conflictingPresentation: String? = null,
            conflictingFile: FirFile? = null,
        ) {
            collect(group.classLikes, conflictingSymbol, conflictingPresentation, conflictingFile)
            collect(group.properties, conflictingSymbol, conflictingPresentation, conflictingFile)

            if (groupHasSimpleFunctions) {
                if (conflictingSymbol !is FirClassLikeSymbol<*>) {
                    return
                }

                conflictingSymbol.expandedClassWithConstructorsScope(context)?.let { (expandedClass, scopeWithConstructors) ->
                    if (expandedClass.classKind == ClassKind.OBJECT || expandedClass.classKind == ClassKind.ENUM_ENTRY) {
                        return
                    }

                    scopeWithConstructors.processDeclaredConstructors { constructor ->
                        val ctorRepresentation = FirRedeclarationPresenter.represent(constructor, conflictingSymbol)
                        collect(group.simpleFunctions, conflictingSymbol = constructor, conflictingPresentation = ctorRepresentation)
                    }
                }
            }
        }

        // Check sources in the order from the table above. Skip the check if all relevant buckets are empty.

        // Function source
        if (groupHasSimpleFunctions || group.constructors.isNotEmpty()) {
            packageMemberScope.processFunctionsByName(declarationName) {
                collect(group.simpleFunctions, it)
                collect(group.constructors, it)
            }
        }

        // Classifier sources, collectForClassifierSource will also check constructors.
        if (groupHasClassLikesOrProperties || groupHasSimpleFunctions) {
            // Scope will only return one classifier per name
            packageMemberScope.processClassifiersByNameWithSubstitution(declarationName) { symbol, _ ->
                collectFromClassifierSource(conflictingSymbol = symbol)
            }

            // session.nameConflictsTracker will contain more classifiers with the same name.
            session.nameConflictsTracker?.let { it as? FirNameConflictsTracker }
                ?.redeclaredClassifiers?.get(ClassId(file.packageFqName, declarationName))?.forEach {
                    collectFromClassifierSource(conflictingSymbol = it.classifier, conflictingFile = it.file)
                }

            // session.nameConflictsTracker doesn't seem to work for LL API for redeclarations in the same file, for this reason
            // we explicitly check classLikes in the same file, too.
            for ((classLike, representation) in group.classLikes) {
                collectFromClassifierSource(classLike, conflictingPresentation = representation, conflictingFile = file)
            }
        }

        // Property source
        if (groupHasClassLikesOrProperties || group.extensionProperties.isNotEmpty()) {
            packageMemberScope.processPropertiesByName(declarationName) {
                collect(group.classLikes, conflictingSymbol = it)
                collect(group.properties, conflictingSymbol = it)
                collect(group.extensionProperties, conflictingSymbol = it)
            }
        }
    }
}

private fun FirClassLikeSymbol<*>.expandedClassWithConstructorsScope(context: CheckerContext): Pair<FirRegularClassSymbol, FirScope>? {
    return when (this) {
        is FirRegularClassSymbol -> this to unsubstitutedScope(context)
        is FirTypeAliasSymbol -> {
            val expandedType = resolvedExpandedTypeRef.coneType as? ConeClassLikeType
            val expandedClass = expandedType?.toRegularClassSymbol(context.session)
            val expandedTypeScope = expandedType?.scope(
                context.session, context.scopeSession,
                CallableCopyTypeCalculator.DoNothing,
                requiredMembersPhase = FirResolvePhase.STATUS,
            )

            if (expandedType != null && expandedClass != null && expandedTypeScope != null) {
                val outerType = outerType(expandedType, context.session) { it.outerClassSymbol(context) }
                expandedClass to TypeAliasConstructorsSubstitutingScope(this, expandedTypeScope, outerType)
            } else {
                null
            }
        }
        else -> null
    }
}

private fun FirDeclarationCollector<FirBasedSymbol<*>>.collectTopLevelConflict(
    declaration: FirBasedSymbol<*>,
    declarationPresentation: String,
    containingFile: FirFile,
    conflictingSymbol: FirBasedSymbol<*>,
    conflictingPresentation: String? = null,
    conflictingFile: FirFile? = null,
) {
    conflictingSymbol.lazyResolveToPhase(FirResolvePhase.STATUS)
    if (conflictingSymbol == declaration || declaration.moduleData != conflictingSymbol.moduleData) return
    val actualConflictingPresentation = conflictingPresentation ?: FirRedeclarationPresenter.represent(conflictingSymbol)
    if (actualConflictingPresentation != declarationPresentation) return
    val actualConflictingFile =
        conflictingFile ?: when (conflictingSymbol) {
            is FirClassLikeSymbol<*> -> session.firProvider.getFirClassifierContainerFileIfAny(conflictingSymbol)
            is FirCallableSymbol<*> -> session.firProvider.getFirCallableContainerFile(conflictingSymbol)
            else -> null
        }
    if (!conflictingSymbol.isCollectable()) return
    if (areCompatibleMainFunctions(declaration, containingFile, conflictingSymbol, actualConflictingFile, session)) return
    @OptIn(SymbolInternals::class)
    val conflicting = conflictingSymbol.fir
    if (
        conflicting is FirMemberDeclaration &&
        !session.visibilityChecker.isVisible(conflicting, session, containingFile, emptyList(), dispatchReceiver = null)
    ) return
    if (isOverloadable(declaration, conflictingSymbol, session)) return

    declarationConflictingSymbols.getOrPut(declaration) { SmartSet.create() }.add(conflictingSymbol)
}

private fun FirNamedFunctionSymbol.representsMainFunctionAllowingConflictingOverloads(session: FirSession): Boolean {
    if (name != StandardNames.MAIN || !callableId.isTopLevel || !hasMainFunctionStatus) return false
    if (receiverParameter != null || typeParameterSymbols.isNotEmpty()) return false
    if (valueParameterSymbols.isEmpty()) return true
    val paramType = valueParameterSymbols.singleOrNull()?.resolvedReturnTypeRef?.coneType?.fullyExpandedType(session) ?: return false
    if (!paramType.isNonPrimitiveArray) return false
    val typeArgument = paramType.typeArguments.singleOrNull() as? ConeKotlinTypeProjection ?: return false
    // only Array<String> and Array<out String> are accepted
    if (typeArgument !is ConeKotlinType && typeArgument !is ConeKotlinTypeProjectionOut) return false
    return typeArgument.type.fullyExpandedType(session).isString
}

private fun areCompatibleMainFunctions(
    declaration1: FirBasedSymbol<*>, file1: FirFile,
    declaration2: FirBasedSymbol<*>, file2: FirFile?,
    session: FirSession,
) = file1 != file2
        && declaration1 is FirNamedFunctionSymbol
        && declaration2 is FirNamedFunctionSymbol
        && declaration1.representsMainFunctionAllowingConflictingOverloads(session)
        && declaration2.representsMainFunctionAllowingConflictingOverloads(session)

private fun isOverloadable(
    declaration: FirBasedSymbol<*>,
    conflicting: FirBasedSymbol<*>,
    session: FirSession,
): Boolean {
    if (isExpectAndActual(declaration, conflicting)) return true

    val declarationIsLowPriority = hasLowPriorityAnnotation(declaration.annotations)
    val conflictingIsLowPriority = hasLowPriorityAnnotation(conflicting.annotations)
    if (declarationIsLowPriority != conflictingIsLowPriority) return true

    return declaration is FirCallableSymbol<*> &&
            conflicting is FirCallableSymbol<*> &&
            session.declarationOverloadabilityHelper.isOverloadable(declaration, conflicting)
}

/** Checks for redeclarations of value and type parameters, and local variables. */
fun checkForLocalRedeclarations(elements: List<FirElement>, context: CheckerContext, reporter: DiagnosticReporter) {
    if (elements.size <= 1) return

    val multimap = ListMultimap<Name, FirBasedSymbol<*>>()

    for (element in elements) {
        val name: Name?
        val symbol: FirBasedSymbol<*>?
        when (element) {
            is FirVariable -> {
                symbol = element.symbol
                name = element.name
            }
            is FirOuterClassTypeParameterRef -> {
                continue
            }
            is FirTypeParameterRef -> {
                symbol = element.symbol
                name = symbol.name
            }
            else -> {
                symbol = null
                name = null
            }
        }
        if (name?.isSpecial == false) {
            multimap.put(name, symbol!!)
        }
    }
    for (key in multimap.keys) {
        val conflictingElements = multimap[key]
        if (conflictingElements.size > 1) {
            for (conflictingElement in conflictingElements) {
                reporter.reportOn(conflictingElement.source, FirErrors.REDECLARATION, conflictingElements, context)
            }
        }
    }
}
