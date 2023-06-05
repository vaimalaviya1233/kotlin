interface WithGeneric<S> {
    fun <T : S> functionWithGeneric(t: T): T

    fun prop(): S
}

fun take(w: WithGeneric<*>) {
    w.<expr>functionWithGeneric</expr>
}