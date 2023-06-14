/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.compiler

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

sealed class CodeFragmentCapturedValue(val name: String, val isMutated: Boolean) {
    open val displayText: String
        get() = name

    override fun toString(): String {
        return javaClass.simpleName + "[name: " + name + "; isMutated: " + isMutated + "; displayText: " + displayText + "]"
    }

    /** Represents a local variable or a parameter. */
    class Local internal constructor(name: Name, isMutated: Boolean) : CodeFragmentCapturedValue(name.asString(), isMutated)

    /** Represents a delegated local variable (`val local by...`). */
    class LocalDelegate internal constructor(name: Name, isMutated: Boolean) : CodeFragmentCapturedValue(name.asString(), isMutated) {
        override val displayText: String
            get() = "$name\$delegate"
    }

    /** Represents a captured outer class. */
    class ContainingClass internal constructor(private val classId: ClassId) : CodeFragmentCapturedValue("<this>", isMutated = false) {
        override val displayText: String
            get() {
                val simpleName = classId.shortClassName
                return if (simpleName.isSpecial) "this" else "this@" + simpleName.asString()
            }
    }

    /** Represents a captured super class (`super.foo()`). */
    class SuperClass internal constructor(private val classId: ClassId) : CodeFragmentCapturedValue("<super>", isMutated = false) {
        override val displayText: String
            get() = "super@" + classId.shortClassName.asString()
    }

    class ExtensionReceiver internal constructor(labelName: String) : CodeFragmentCapturedValue(labelName, isMutated = false) {
        override val displayText: String
            get() = "this@$name"
    }

    class ContextReceiver internal constructor(labelName: Name) : CodeFragmentCapturedValue(labelName.asString(), isMutated = false) {
        override val displayText: String
            get() = "this@$name"
    }

    /** Represents a captured named local function. */
    class LocalFunction internal constructor(name: Name) : CodeFragmentCapturedValue(name.asString(), isMutated = false)

    /** Represents a `_DebugLabel` synthetic debugger variable, created by the "Mark object" action. */
    class DebugLabel internal constructor(name: Name, isMutated: Boolean) : CodeFragmentCapturedValue(name.asString(), isMutated) {
        override val displayText: String
            get() = name + "_DebugLabel"
    }

    /** Represents a '_field' synthetic debugger variable. */
    class FieldVariable internal constructor(name: Name, isMutated: Boolean) : CodeFragmentCapturedValue(name.asString(), isMutated) {
        override val displayText: String
            get() = name + "_field"
    }

    class FakeJavaOuterClass internal constructor(name: Name) : CodeFragmentCapturedValue(name.asString(), isMutated = false) {
        override val displayText: String
            get() = "this"
    }

    /** Represents a `coroutineContext` call. */
    object CoroutineContext : CodeFragmentCapturedValue("coroutineContext", isMutated = false)
}