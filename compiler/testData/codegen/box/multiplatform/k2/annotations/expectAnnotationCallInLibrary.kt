// !LANGUAGE: +MultiPlatformProjects
// IGNORE_BACKEND_K1: JS, JS_IR, JS_IR_ES6, NATIVE
// ISSUE: KT-57963

// MODULE: common
// TARGET_PLATFORM: Common
// FILE: common.kt

expect annotation class Ann constructor()

@Ann val x = "OK"

// MODULE: lib()()(common)
// FILE: lib.kt

actual annotation class Ann

// MODULE: main(lib)
// FILE: main.kt

fun box() = x