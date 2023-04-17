// ISSUE: KT-58008

object Retry {
    data class Builder<B>(
        private val action: suspend () -> B,
    )

    fun <W> withExponentialBackoff(action: () -> W): Builder<W> {
        return Builder(action)
    }
}
