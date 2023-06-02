/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.buildtools.internal

import org.jetbrains.kotlin.buildtools.api.CompilerExecutionStrategyConfig
import java.io.File

class CompilerExecutionStrategyConfigImpl : CompilerExecutionStrategyConfig {
    override fun useInProcessStrategy(): CompilerExecutionStrategyConfig {
        return this
    }

    override fun useDaemonStrategy(sessionDir: File, jvmArguments: List<String>): CompilerExecutionStrategyConfig {
        TODO("Daemon strategy is not yet supported in the Build Tools API")
    }
}