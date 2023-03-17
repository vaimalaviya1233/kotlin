// TARGET_BACKEND: JVM_IR
// IGNORE_BACKEND_K2: JVM_IR
interface Invariant<A>

fun Invariant<in Number>.publicFunc() = privateFunc()

private fun <B> Invariant<B>.privateFunc() = object : Invariant<B> {
    override fun toString(): String = "OK"
}

fun box() = (object : Invariant<Number> {}).publicFunc().toString()
