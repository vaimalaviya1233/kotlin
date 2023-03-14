// ISSUE: KT-57105

class RootBus: MessageBusImpl() {
    override val parentBus: Any? = "OK"
}

open class MessageBusImpl {
    open val parentBus: Any?

    init {
        run {
            this@MessageBusImpl as RootBus
            this@MessageBusImpl.parentBus = "FAIL"
        }
    }
}

fun box(): String {
    return RootBus().parentBus as String
}
