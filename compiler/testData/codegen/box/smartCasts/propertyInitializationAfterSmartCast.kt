// ISSUE: KT-57105

class RootBus: MessageBusImpl()

open class MessageBusImpl {
    val parentBus: Any?

    init {
        this as RootBus
        parentBus = "OK"
    }
}

class RootBus2: MessageBusImpl2() {
    override val parentBus: Any? get() = "OK"
}

open class MessageBusImpl2 {
    open val parentBus: Any?

    init {
        this as RootBus2
        parentBus = "FAIL"
    }
}

class RootBus3: MessageBusImpl3() {
    override val parentBus: Any? = "OK"
}

open class MessageBusImpl3 {
    open val parentBus: Any?

    init {
        this as RootBus3
        parentBus = "FAIL"
    }
}

fun box(): String {
    if (RootBus().parentBus != "OK") return "FAIL 1"
    if (RootBus2().parentBus != "OK") return "FAIL 2"
    if (RootBus3().parentBus != "OK") return "FAIL 3"

    return "OK"
}
