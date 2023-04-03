// EXPECTED_REACHABLE_NODES: 1283
package foo

// CHECK_CONTAINS_NO_CALLS: test
// CHECK_VARS_COUNT: function=test count=2 TARGET_BACKENDS=JS
// CHECK_VARS_COUNT: function=test count=4 TARGET_BACKENDS=JS_IR

internal inline fun sign(x: Int): Int {
    if (x < 0) return -1

    if (x == 0) return 0

    return 1
}

internal fun test(x: Int, y: Int): Int {
    if (x != 0) {
        return sign(x)
    }

    return sign(y)
}

fun box(): String {
    assertEquals(-1, test(-2, 2))
    assertEquals(1, test(2, -2))
    assertEquals(-1, test(0, -2))
    assertEquals(1, test(0, 2))
    assertEquals(0, test(0, 0))

    return "OK"
}