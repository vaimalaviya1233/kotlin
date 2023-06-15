/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library.abi.internal.impl

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.library.abi.internal.*
import java.io.File
import java.util.*

/**
 * TODO: This is a mock temporary implementation to be replaced by a real one.
 */
internal object MockLibraryAbiReader {
    fun readAbiInfo(library: File): LibraryAbi {
        return LibraryAbi(
            manifest = LibraryManifest(
                uniqueName = library.nameWithoutExtension,
                platform = "NATIVE",
                nativeTargets = sortedSetOf("ios_arm64"),
                compilerVersion = "1.9.20",
                abiVersion = "1.8.0",
                libraryVersion = "0.0.1",
                irProvider = "fake-ir-provider"
            ),
            supportedSignatureVersions = AbiSignatureVersion.entries.toSortedSet(),
            topLevelDeclarations = generateDeclarations()
        )
    }

    /*
    fun topLevelFun(): String = ""
    fun topLevelFun(a: Int): String = ""
    fun topLevelFun(a: Int, b: Long): String = ""
    fun Int.topLevelFun(): String = ""
    context(Int) fun topLevelFun(): String = ""
    context(Int, Long) fun topLevelFun(): String = ""
    context(Int, Long) fun Int.topLevelFun(): String = ""
    context(Int, Long) fun Int.topLevelFun(a: Int, b: Long): String = ""

    suspend fun suspendTopLevelFun(a: Int): String = ""
    fun expectTopLevelFun(a: Int): String = ""

    fun topLevelVarargFun(vararg a: Int): String = ""
    fun topLevelVarargFun(a: Array<Int>): String = ""
    fun topLevelVarargFun(a: Array<out Int>): String = ""
    fun topLevelFunWithDefaults(a: Int = 42, b: Long = -42): String = ""
    inline fun topLevelInlineFun(
        inlineBlock: (Int) -> String,
        noinline noinlineBlock: (Int) -> String,
        crossinline crossinlineBlock: (Int) -> String
    ): String = ""

    fun overloadedTopLevelFun(a: String): String = ""
    fun <T> overloadedTopLevelFun(a: String): String = ""
    fun <T> overloadedTopLevelFun(a: T): String = ""
    fun <T : Any> overloadedTopLevelFun(a: T): String = ""
    fun <T : Any> overloadedTopLevelFun(a: T?): String = ""
    fun <T : CharSequence> overloadedTopLevelFun(a: T): String = ""
    fun <T> overloadedTopLevelFun(a: T): String where T : CharSequence, T : Appendable = ""

    var topLevelVar: String
        get() = ""
        set(_) = Unit

    var Int.topLevelVar: String
        get() = ""
        set(_) = Unit

    val topLevelVal: String = ""
    const val topLevelConstVal: String = ""

    class Class {
        fun funOfClass(): String = ""
        val valOfClass: String get() = ""
        class Nested {
            fun funOfNested(): String = ""
            val valOfNested: String get() = ""
            class NestedOfNested {
                fun funOfNestedOfNested(): String = ""
                val valOfNestedOfNested: String get() = ""
            }
            inner class InnerOfNested {
                fun funOfInnerOfNested(): String = ""
                val valOfInnerOfNested: String get() = ""
            }
        }
        inner class Inner {
            fun funOfInner(): String = ""
            val valOfInner: String get() = ""
            inner class InnerOfInner {
                fun funOfInnerOfInner(): String = ""
                val valOfInnerOfInner: String get() = ""
            }
        }
        companion object
    }

    value class ValueClass(val p: Int)
    annotation class AnnotationClass
    object Object
    interface Interface
    fun interface FunctionInterface { fun foo() }
    abstract class AbstractClass : Interface, FunctionInterface

    enum class EnumClass {
        ENTRY1 { override fun foo() = Unit },
        ENTRY2 { override fun foo() = Unit };
        abstract fun foo()
    }
    */
    private fun generateDeclarations(): AbiTopLevelDeclarations = topLevels(
        abstractClass(
            "klib.abi.test/AbstractClass|null[0]",
            "klib.abi.test/AbstractClass",
            superTypes = sortedSetOf(
                "klib.abi.test/FunctionInterface",
                "klib.abi.test/Interface"
            ),
            constructor(
                "klib.abi.test/AbstractClass.<init>|-5645683436151566731[0]",
                "klib.abi.test/AbstractClass.^c()"
            )
        ),
        annotationClass(
            "klib.abi.test/AnnotationClass|null[0]",
            "klib.abi.test/AnnotationClass",
            constructor(
                "klib.abi.test/AnnotationClass.<init>|-5645683436151566731[0]",
                "klib.abi.test/AnnotationClass.^c()"
            )
        ),
        finalClass(
            "klib.abi.test/Class|null[0]",
            "klib.abi.test/Class",
            constructor(
                "klib.abi.test/Class.<init>|-5645683436151566731[0]",
                "klib.abi.test/Class.^c()"
            ),
            finalVal(
                "klib.abi.test/Class.valOfClass|433973585083875737[0]",
                "klib.abi.test/Class.valOfClass=kotlin/String"
            ),
            finalFun(
                "klib.abi.test/Class.valOfClass.<get-valOfClass>|5369084220387716246[0]",
                "klib.abi.test/Class.valOfClass.^g\$()=kotlin/String"
            ),
            finalFun(
                "klib.abi.test/Class.funOfClass|-797013061710964143[0]",
                "klib.abi.test/Class.funOfClass\$()=kotlin/String"
            ),
            `object`(
                "klib.abi.test/Class.Companion|null[0]",
                "klib.abi.test/Class.Companion"
            ),
            finalInnerClass(
                "klib.abi.test/Class.Inner|null[0]",
                "klib.abi.test/Class.Inner",
                constructor(
                    "klib.abi.test/Class.Inner.<init>|-5645683436151566731[0]",
                    "klib.abi.test/Class.Inner.^c\$()"
                ),
                finalVal(
                    "klib.abi.test/Class.Inner.valOfInner|7828569960507871692[0]",
                    "klib.abi.test/Class.Inner.valOfInner=kotlin/String"
                ),
                finalFun(
                    "klib.abi.test/Class.Inner.valOfInner.<get-valOfInner>|-2946052066369038659[0]",
                    "klib.abi.test/Class.Inner.valOfInner.^g\$()=kotlin/String"
                ),
                finalFun(
                    "klib.abi.test/Class.Inner.funOfInner|3010729524886683981[0]",
                    "klib.abi.test/Class.Inner.funOfInner\$()=kotlin/String"
                ),
                finalInnerClass(
                    "klib.abi.test/Class.Inner.InnerOfInner|null[0]",
                    "klib.abi.test/Class.Inner.InnerOfInner",
                    constructor(
                        "klib.abi.test/Class.Inner.InnerOfInner.<init>|-5645683436151566731[0]",
                        "klib.abi.test/Class.Inner.InnerOfInner.^c\$()"
                    ),
                    finalVal(
                        "klib.abi.test/Class.Inner.InnerOfInner.valOfInnerOfInner|-7036138277816820418[0]",
                        "klib.abi.test/Class.Inner.InnerOfInner.valOfInnerOfInner=kotlin/String"
                    ),
                    finalFun(
                        "klib.abi.test/Class.Inner.InnerOfInner.valOfInnerOfInner.<get-valOfInnerOfInner>|4226646688399547436[0]",
                        "klib.abi.test/Class.Inner.InnerOfInner.valOfInnerOfInner.^g\$()=kotlin/String"
                    ),
                    finalFun(
                        "klib.abi.test/Class.Inner.InnerOfInner.funOfInnerOfInner|-1588191082896750619[0]",
                        "klib.abi.test/Class.Inner.InnerOfInner.funOfInnerOfInner\$()=kotlin/String"
                    )
                )
            ),
            finalClass(
                "klib.abi.test/Class.Nested|null[0]",
                "klib.abi.test/Class.Nested",
                constructor(
                    "klib.abi.test/Class.Nested.<init>|-5645683436151566731[0]",
                    "klib.abi.test/Class.Nested.^c()"
                ),
                finalVal(
                    "klib.abi.test/Class.Nested.valOfNested|-5321144969073740296[0]",
                    "klib.abi.test/Class.Nested.valOfNested=kotlin/String"
                ),
                finalFun(
                    "klib.abi.test/Class.Nested.valOfNested.<get-valOfNested>|-5624075254170403138[0]",
                    "klib.abi.test/Class.Nested.valOfNested.^g\$()=kotlin/String"
                ),
                finalFun(
                    "klib.abi.test/Class.Nested.funOfNested|-1263241914935971934[0]",
                    "klib.abi.test/Class.Nested.funOfNested\$()=kotlin/String"
                ),
                finalInnerClass(
                    "klib.abi.test/Class.Nested.InnerOfNested|null[0]",
                    "klib.abi.test/Class.Nested.InnerOfNested",
                    constructor(
                        "klib.abi.test/Class.Nested.InnerOfNested.<init>|-5645683436151566731[0]",
                        "klib.abi.test/Class.Nested.InnerOfNested.^c\$()"
                    ),
                    finalVal(
                        "klib.abi.test/Class.Nested.InnerOfNested.valOfInnerOfNested|-7130048892740966353[0]",
                        "klib.abi.test/Class.Nested.InnerOfNested.valOfInnerOfNested=kotlin/String"
                    ),
                    finalFun(
                        "klib.abi.test/Class.Nested.InnerOfNested.valOfInnerOfNested.<get-valOfInnerOfNested>|2771438287613059430[0]",
                        "klib.abi.test/Class.Nested.InnerOfNested.valOfInnerOfNested.^g\$()=kotlin/String"
                    ),
                    finalFun(
                        "klib.abi.test/Class.Nested.InnerOfNested.funOfInnerOfNested|2020651343652422702[0]",
                        "klib.abi.test/Class.Nested.InnerOfNested.funOfInnerOfNested\$()=kotlin/String"
                    )
                ),
                finalClass(
                    "klib.abi.test/Class.Nested.NestedOfNested|null[0]",
                    "klib.abi.test/Class.Nested.NestedOfNested",
                    constructor(
                        "klib.abi.test/Class.Nested.NestedOfNested.<init>|-5645683436151566731[0]",
                        "klib.abi.test/Class.Nested.NestedOfNested.^c()"
                    ),
                    finalVal(
                        "klib.abi.test/Class.Nested.NestedOfNested.valOfNestedOfNested|3623806239156077906[0]",
                        "klib.abi.test/Class.Nested.NestedOfNested.valOfNestedOfNested=kotlin/String"
                    ),
                    finalFun(
                        "klib.abi.test/Class.Nested.NestedOfNested.valOfNestedOfNested.<get-valOfNestedOfNested>|-3381702863193046068[0]",
                        "klib.abi.test/Class.Nested.NestedOfNested.valOfNestedOfNested.^g\$()=kotlin/String"
                    ),
                    finalFun(
                        "klib.abi.test/Class.Nested.NestedOfNested.funOfNestedOfNested|-1104359515243763750[0]",
                        "klib.abi.test/Class.Nested.NestedOfNested.funOfNestedOfNested\$()=kotlin/String"
                    )
                )
            )
        ),
        enumClass(
            "klib.abi.test/EnumClass|null[0]",
            "klib.abi.test/EnumClass",
            enumEntry(
                "klib.abi.test/EnumClass.ENTRY1|null[0]",
                "klib.abi.test/EnumClass.ENTRY1"
            ),
            enumEntry(
                "klib.abi.test/EnumClass.ENTRY2|null[0]",
                "klib.abi.test/EnumClass.ENTRY2"
            ),
            abstractFun(
                "klib.abi.test/EnumClass.foo|-1041209573719867811[0]",
                "klib.abi.test/EnumClass.foo\$()=kotlin/Unit"
            )
        ),
        funInterface(
            "klib.abi.test/FunctionInterface|null[0]",
            "klib.abi.test/FunctionInterface",
            abstractFun(
                "klib.abi.test/FunctionInterface.foo|-1041209573719867811[0]",
                "klib.abi.test/FunctionInterface.foo\$()=kotlin/Unit"
            )
        ),
        `interface`(
            "klib.abi.test/Interface|null[0]",
            "klib.abi.test/Interface"
        ),
        `object`(
            "klib.abi.test/Object|null[0]",
            "klib.abi.test/Object"
        ),
        valueClass(
            "klib.abi.test/ValueClass|null[0]",
            "klib.abi.test/ValueClass",
            constructor(
                "klib.abi.test/ValueClass.<init>|-5182794243525578284[0]",
                "klib.abi.test/ValueClass.^c(kotlin/Int)"
            ),
            finalVal(
                "klib.abi.test/ValueClass.p|6715504260787941082[0]",
                "klib.abi.test/ValueClass.p=kotlin/Int"
            ),
            finalFun(
                "klib.abi.test/ValueClass.p.<get-p>|-1162552463316289847[0]",
                "klib.abi.test/ValueClass.p.^g\$()=kotlin/Int"
            )
        ),
        finalVar(
            "klib.abi.test/topLevelVar|7956002610571589851[0]",
            "klib.abi.test/topLevelVar=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelVar.<get-topLevelVar>|4171232910088346368[0]",
            "klib.abi.test/topLevelVar.^g()=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelVar.<set-topLevelVar>|-8754680581387542939[0]",
            "klib.abi.test/topLevelVar.^s(kotlin/String)"
        ),
        finalVar(
            "klib.abi.test/topLevelVar|2818339884836210153[0]",
            "klib.abi.test/topLevelVar@kotlin/Int=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelVar.<get-topLevelVar>|3024882152913336936[0]",
            "klib.abi.test/topLevelVar@kotlin/Int.^g()=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelVar.<set-topLevelVar>|5463347746919915819[0]",
            "klib.abi.test/topLevelVar@kotlin/Int.^s(kotlin/String)"
        ),
        finalVal(
            "klib.abi.test/topLevelVal|-3803935533547561652[0]",
            "klib.abi.test/topLevelVal=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelVal.<get-topLevelVal>|2084281846661455900[0]",
            "klib.abi.test/topLevelVal.^g()=kotlin/String"
        ),
        finalConstVal(
            "klib.abi.test/topLevelConstVal|-2477645097827337477[0]",
            "klib.abi.test/topLevelConstVal=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelConstVal.<get-topLevelConstVal>|8000506644318128252[0]",
            "klib.abi.test/topLevelConstVal.^g()=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/overloadedTopLevelFun|-1909424610827250362[0]",
            "klib.abi.test/overloadedTopLevelFun(kotlin/String)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/overloadedTopLevelFun|-3575352618111393371[0]",
            "klib.abi.test/overloadedTopLevelFun<kotlin/Any?>(kotlin/String)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/overloadedTopLevelFun|-1587513576983547699[0]",
            "klib.abi.test/overloadedTopLevelFun<kotlin/Any?>(#0)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/overloadedTopLevelFun|4890147970487595330[0]",
            "klib.abi.test/overloadedTopLevelFun<kotlin/Any>(#0)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/overloadedTopLevelFun|-9202363577444565286[0]",
            "klib.abi.test/overloadedTopLevelFun<kotlin/Any>(#0?)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/overloadedTopLevelFun|-600727038924012581[0]",
            "klib.abi.test/overloadedTopLevelFun<kotlin/CharSequence>(#0)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/overloadedTopLevelFun|-2971508381780377198[0]",
            "klib.abi.test/overloadedTopLevelFun<kotlin/Appendable&kotlin/CharSequence>(#0)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/suspendTopLevelFun|-2671426082158503799[0]",
            "klib.abi.test/suspendTopLevelFun(kotlin/Int)=kotlin/String|s"
        ),
        finalFun(
            "klib.abi.test/expectTopLevelFun|-8029371211400495179[1]",
            "klib.abi.test/expectTopLevelFun(kotlin/Int)=kotlin/String|e"
        ),
        finalFun(
            "klib.abi.test/topLevelVarargFun|-8062578169479577844[0]",
            "klib.abi.test/topLevelVarargFun(kotlin/Int...)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelVarargFun|-1977594530768978081[0]",
            "klib.abi.test/topLevelVarargFun(kotlin/Array<kotlin/Int>)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelVarargFun|5428601736755292589[0]",
            "klib.abi.test/topLevelVarargFun(kotlin/Array<-kotlin/Int>)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelFunWithDefaults|1159926268696030611[0]",
            "klib.abi.test/topLevelFunWithDefaults(kotlin/Int;kotlin/Long)=kotlin/String",
            valueParameterFlags(
                0 to AbiFunction.ValueParameterFlag.HAS_DEFAULT_ARG,
                1 to AbiFunction.ValueParameterFlag.HAS_DEFAULT_ARG
            )
        ),
        inlineFun(
            "klib.abi.test/topLevelInlineFun|-5396238017896868274[0]",
            "klib.abi.test/topLevelInlineFun(kotlin/Function1<kotlin/Int;kotlin/String>;kotlin/Function1<kotlin/Int;kotlin/String>;kotlin/Function1<kotlin/Int;kotlin/String>)",
            valueParameterFlags(
                1 to AbiFunction.ValueParameterFlag.NOINLINE,
                2 to AbiFunction.ValueParameterFlag.CROSSINLINE
            )
        ),
        finalFun(
            "klib.abi.test/topLevelFun|-622527295593064409[0]",
            "klib.abi.test/topLevelFun()=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelFun|6933303545227536755[0]",
            "klib.abi.test/topLevelFun(kotlin/Int)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelFun|6240270410835079577[0]",
            "klib.abi.test/topLevelFun(kotlin/Int;kotlin/Long)=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelFun|-1131436401381962866[0]",
            "klib.abi.test/topLevelFun@kotlin/Int()=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelFun|-6933665416149205282[0]",
            "klib.abi.test/topLevelFun{kotlin/Int}()=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelFun|2726666196939267226[0]",
            "klib.abi.test/topLevelFun{kotlin/Int;kotlin/Long}()=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelFun|294059116065521812[0]",
            "klib.abi.test/topLevelFun{kotlin/Int;kotlin/Long}@kotlin/Int()=kotlin/String"
        ),
        finalFun(
            "klib.abi.test/topLevelFun|7086915696014031501[0]",
            "klib.abi.test/topLevelFun{kotlin/Int;kotlin/Long}@kotlin/Int(kotlin/Int;kotlin/Long)=kotlin/String"
        ),
    )
}

private fun topLevels(vararg declarations: AbiDeclaration) = AbiTopLevelDeclarationsImpl(
    declarations = declarations.toList()
)

private fun constructor(sv1: String, sv2: String) = AbiFunctionImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    isConstructor = true,
    isInline = false,
    valueParameterFlags = null
)

private fun finalVal(sv1: String, sv2: String) = AbiPropertyImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    mutability = AbiProperty.Mutability.VAL
)

private fun finalConstVal(sv1: String, sv2: String) = AbiPropertyImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    mutability = AbiProperty.Mutability.CONST_VAL
)

private fun finalVar(sv1: String, sv2: String) = AbiPropertyImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    mutability = AbiProperty.Mutability.VAR
)

private fun valueParameterFlags(vararg flags: Pair<Int, AbiFunction.ValueParameterFlag>): AbiFunction.ValueParameterFlags =
    if (flags.isEmpty()) {
        AbiFunction.ValueParameterFlags(emptyList())
    } else {
        val maxIndex = flags.maxOf { it.first }
        val map = flags.associate { it.first to sortedSetOf(it.second) }
        val list = List<SortedSet<AbiFunction.ValueParameterFlag>>(maxIndex + 1) { index ->
            map[index] ?: sortedSetOf()
        }
        AbiFunction.ValueParameterFlags(list)
    }

private fun finalFun(sv1: String, sv2: String, valueParameterFlags: AbiFunction.ValueParameterFlags? = null) = AbiFunctionImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    isConstructor = false,
    isInline = false,
    valueParameterFlags = valueParameterFlags
)

private fun inlineFun(sv1: String, sv2: String, valueParameterFlags: AbiFunction.ValueParameterFlags? = null) = AbiFunctionImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    isConstructor = false,
    isInline = true,
    valueParameterFlags = valueParameterFlags
)

private fun abstractFun(sv1: String, sv2: String) = AbiFunctionImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.ABSTRACT,
    isConstructor = false,
    isInline = false,
    valueParameterFlags = null
)

private fun abstractClass(sv1: String, sv2: String, superTypes: SortedSet<AbiSuperType>, vararg declarations: AbiDeclaration) =
    AbiClassImpl(
        signatures = AbiSignaturesImpl(sv1, sv2),
        modality = Modality.ABSTRACT,
        kind = ClassKind.CLASS,
        isInner = false,
        isValue = false,
        isFunction = false,
        superTypes = superTypes,
        declarations = declarations.toList()
    )

private fun annotationClass(sv1: String, sv2: String, vararg declarations: AbiDeclaration) = AbiClassImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    kind = ClassKind.ANNOTATION_CLASS,
    isInner = false,
    isValue = false,
    isFunction = false,
    superTypes = sortedSetOf(),
    declarations = declarations.toList()
)

private fun finalClass(sv1: String, sv2: String, vararg declarations: AbiDeclaration) = AbiClassImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    kind = ClassKind.CLASS,
    isInner = false,
    isValue = false,
    isFunction = false,
    superTypes = sortedSetOf(),
    declarations = declarations.toList()
)

private fun finalInnerClass(sv1: String, sv2: String, vararg declarations: AbiDeclaration) = AbiClassImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    kind = ClassKind.CLASS,
    isInner = true,
    isValue = false,
    isFunction = false,
    superTypes = sortedSetOf(),
    declarations = declarations.toList()
)

private fun enumClass(sv1: String, sv2: String, vararg declarations: AbiDeclaration) = AbiClassImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    kind = ClassKind.ENUM_CLASS,
    isInner = false,
    isValue = false,
    isFunction = false,
    superTypes = sortedSetOf(),
    declarations = declarations.toList()
)

private fun enumEntry(sv1: String, sv2: String, vararg declarations: AbiDeclaration) = AbiClassImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    kind = ClassKind.ENUM_ENTRY,
    isInner = false,
    isValue = false,
    isFunction = false,
    superTypes = sortedSetOf(),
    declarations = declarations.toList()
)

private fun `object`(sv1: String, sv2: String, vararg declarations: AbiDeclaration) = AbiClassImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    kind = ClassKind.OBJECT,
    isInner = false,
    isValue = false,
    isFunction = false,
    superTypes = sortedSetOf(),
    declarations = declarations.toList()
)

private fun `interface`(sv1: String, sv2: String, vararg declarations: AbiDeclaration) = AbiClassImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.ABSTRACT,
    kind = ClassKind.INTERFACE,
    isInner = false,
    isValue = false,
    isFunction = false,
    superTypes = sortedSetOf(),
    declarations = declarations.toList()
)

private fun funInterface(sv1: String, sv2: String, vararg declarations: AbiDeclaration) = AbiClassImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.ABSTRACT,
    kind = ClassKind.INTERFACE,
    isInner = false,
    isValue = false,
    isFunction = true,
    superTypes = sortedSetOf(),
    declarations = declarations.toList()
)

private fun valueClass(sv1: String, sv2: String, vararg declarations: AbiDeclaration) = AbiClassImpl(
    signatures = AbiSignaturesImpl(sv1, sv2),
    modality = Modality.FINAL,
    kind = ClassKind.CLASS,
    isInner = false,
    isValue = true,
    isFunction = false,
    superTypes = sortedSetOf(),
    declarations = declarations.toList()
)

