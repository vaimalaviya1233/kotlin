// FILE: main.kt
package test

import dependency.function1
import dependency.function2
import dependency.function3

/**
 * [function1]
 * [Int.function2]
 * [dependency.function3]
 */
fun usage() {}

// FILE: dependency.kt
package dependency

fun Int.function1() {}
fun Int.function2() {}
fun Int.function3() {}
