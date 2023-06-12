// WITH_STDLIB

fun foo() {
    <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.collections.List<IOT>")!>foo(1 to 2)<!>
}

fun <T : Comparable<T>> foo(vararg values: Pair<T, T>): List<T> = TODO()
