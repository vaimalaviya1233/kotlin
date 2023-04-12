// !LANGUAGE: +MultiPlatformProjects
// WITH_STDLIB
// IGNORE_BACKEND_K1: NATIVE, WASM
// ISSUE: KT-57923

// MODULE: lib
// FILE: lib.kt

import kotlin.jvm.*

class A(@Volatile var x: String)

// MODULE: main(lib)
// FILE: main.kt

fun box() = A("OK").x