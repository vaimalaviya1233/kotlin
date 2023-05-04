// IGNORE_BACKEND_K1: JS, JS_IR, JS_IR_ES6, NATIVE
// !LANGUAGE: +MultiPlatformProjects
// ISSUE: KT-58229

// MODULE: common
// FILE: common.kt

expect class CancellationException

interface ReceiveChannel<E> {
    fun cancel(cause: CancellationException)
}

interface Channel: ReceiveChannel<Any?>

fun box() = "OK"

// MODULE: platform()()(common)
// FILE: platform.kt

actual typealias CancellationException = Something

class Something

fun foo(channel: Channel) {
    channel.cancel(Something())
}