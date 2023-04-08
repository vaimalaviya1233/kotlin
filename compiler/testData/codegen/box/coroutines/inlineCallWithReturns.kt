// WITH_STDLIB
// IGNORE_BACKEND: WASM

import kotlin.coroutines.*

suspend fun <T> withCorutine(block: suspend () -> Unit): Unit {
    block()
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(Continuation(EmptyCoroutineContext) {
        it.getOrThrow()
    })
}

inline fun f(): Int {
    if (42 != 42) return 0
    return 1
}

fun box(): String {
    builder {
        check(f() == 1)
    }
    return "OK"
}