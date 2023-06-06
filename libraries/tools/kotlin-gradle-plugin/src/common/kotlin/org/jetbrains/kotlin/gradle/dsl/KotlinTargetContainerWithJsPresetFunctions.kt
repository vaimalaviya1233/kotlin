/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl

import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension.Companion.reportJsCompilerMode
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

interface KotlinTargetContainerWithJsPresetFunctions :
    KotlinTargetsContainerWithPresets,
    KotlinJsCompilerTypeHolder {
    fun js(
        name: String = "js",
        compiler: KotlinJsCompilerType = defaultJsCompilerType,
        configure: KotlinJsTargetDsl.() -> Unit = { }
    ): KotlinJsTargetDsl = jsInternal(
        name,
        compiler,
        configure
    )

    fun js(
        name: String,
        compiler: String,
        configure: KotlinJsTargetDsl.() -> Unit = { }
    ) = js(
        name = name,
        compiler = KotlinJsCompilerType.byArgument(compiler),
        configure = configure
    )

    fun js(
        name: String = "js",
        configure: KotlinJsTargetDsl.() -> Unit = { }
    ) = jsInternal(name = name, configure = configure)

    fun js(
        compiler: KotlinJsCompilerType,
        configure: KotlinJsTargetDsl.() -> Unit = { }
    ) = js(name = "js", compiler = compiler, configure = configure)

    fun js() = jsInternal(name = "js") { }
    fun js(name: String) = jsInternal(name = name) { }
    fun js(name: String, configure: Action<KotlinJsTargetDsl>) = jsInternal(name = name) { configure.execute(this) }
    fun js(compiler: KotlinJsCompilerType, configure: Action<KotlinJsTargetDsl>) = js(compiler = compiler) {
        configure.execute(this)
    }

    fun js(configure: Action<KotlinJsTargetDsl>) = jsInternal { configure.execute(this) }

    fun js(
        name: String,
        compiler: KotlinJsCompilerType,
        configure: Action<KotlinJsTargetDsl>
    ) = js(
        name = name,
        compiler = compiler
    ) {
        configure.execute(this)
    }

    fun js(
        name: String,
        compiler: String,
        configure: Action<KotlinJsTargetDsl>
    ) = js(
        name = name,
        compiler = compiler
    ) {
        configure.execute(this)
    }
}

private fun KotlinTargetContainerWithJsPresetFunctions.jsInternal(
    name: String = "js",
    compiler: KotlinJsCompilerType? = null,
    configure: KotlinJsTargetDsl.() -> Unit
): KotlinJsTargetDsl {
    val compilerOrDefault = compiler
        ?: defaultJsCompilerType

    reportJsCompilerMode(compilerOrDefault)

    @Suppress("UNCHECKED_CAST")
    return configureOrCreate(
        name,
        presets.getByName(
            "js"
        ) as KotlinTargetPreset<KotlinJsTargetDsl>,
        configure
    )
}
