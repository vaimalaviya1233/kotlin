/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.native

/**
 * Worker API is obsolete. Consider using [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) instead.
 *
 * See [coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html) to get started with coroutines.
 */
// TODO: A separate doc for Workers API to kotlinx.coroutines API migration.
@SinceKotlin("1.8")
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public actual annotation class ObsoleteWorkersApi
