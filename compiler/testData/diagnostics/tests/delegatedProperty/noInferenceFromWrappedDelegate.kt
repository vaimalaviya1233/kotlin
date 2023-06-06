// FIR_IDENTICAL
// FIR_DUMP
// WITH_REFLECT
// DIAGNOSTICS: -NOTHING_TO_INLINE

import kotlin.reflect.KProperty

// Definitions
class State<S>(var value: S)
inline operator fun <V> State<V>.getValue(thisRef: Any?, property: KProperty<*>): V = value
inline operator fun <V> State<V>.setValue(thisRef: Any?, property: KProperty<*>, v: V) { }
inline fun <M> remember(block: () -> M): M = block()

val list by remember { State(listOf(0)) }
val first = list.first()

val list2 by State(listOf(0))
val first2 = list2.first()

var mutableList by remember { State(listOf(0)) }
val mutableFirst = mutableList.first()

var mutableList2 by State(listOf(0))
val mutableFirst2 = mutableList2.first()

fun test() {
    val list by remember { State(listOf(0)) }
    list.first()

    val list2 by State(listOf(0))
    list2.first()

    var mutableList by remember { State(listOf(0)) }
    mutableList.first()
    mutableList = listOf(1)

    var mutableList2 by State(listOf(0))
    mutableList2.first()
    mutableList2 = listOf(1)
}
