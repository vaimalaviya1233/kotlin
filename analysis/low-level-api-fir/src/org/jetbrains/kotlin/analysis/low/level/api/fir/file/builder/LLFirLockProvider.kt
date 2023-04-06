/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.file.builder

import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.llFirSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.lockWithPCECheck
import org.jetbrains.kotlin.fir.declarations.FirFile
import java.util.concurrent.locks.ReentrantLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class LLFirLockProvider {
    //We temporarily disable multi-locks to fix deadlocks problem
    private val globalLock = ReentrantLock()

    inline fun <R> withLock(
        key: FirFile,
        lockingIntervalMs: Long = DEFAULT_LOCKING_INTERVAL,
        action: () -> R
    ): R {
        return globalLock.lockWithPCECheck(lockingIntervalMs) {
            val session = key.llFirSession
            if (!session.isValid && shouldRetryFlag.get()) {
                val description = session.ktModule.moduleDescription
                throw InvalidSessionException("Session '$description' is invalid")
            }

            // Normally, analysis should not be allowed on an invalid session.
            // However, there isn't an easy way to cancel or redo it in general case, as it must then be supported on use-site.
            withRetryFlag(false, action)
        }
    }
}

private const val DEFAULT_LOCKING_INTERVAL = 50L

internal class InvalidSessionException(message: String?) : RuntimeException(message)

/*
    The flag specifies whether the analysis action should be repeated in case if it was originally started on an invalid session.

    Possible values:
        - `true` – throw the marker [InvalidSessionException] to trigger the retry.
        - `false` – process analysis as usual (default).
 */
private val shouldRetryFlag: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

/**
 * Retry the `action` calculation with a new FIR session if session passed to [LLFirLockProvider.withLock] turns to be invalid.
 * This is a temporary solution to fix inconsistent analysis state in common cases of idempotent analysis.
 * The right solution would be to modify the FIR tree after the analysis is done, so the tree will always be in consistent state.
 */
internal inline fun <R> retryOnInvalidSession(action: () -> R): R {
    withRetryFlag(true) {
        while (true) {
            try {
                return action()
            } catch (ignore: InvalidSessionException) {}
        }
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <R> withRetryFlag(value: Boolean, action: () -> R): R {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }

    val oldValue = shouldRetryFlag.get()
    shouldRetryFlag.set(value)
    try {
        return action()
    } finally {
        shouldRetryFlag.set(oldValue)
    }
}