// FIR_IDENTICAL
class AtomicRef<T>(val value: T)

inline fun <F : Segment<F>> AtomicRef<F>.findSegmentAndMoveForward(
    startFrom: F,
    createNewSegment: (prev: F?) -> F
) {
    createNewSegment(startFrom)
}

interface Queue<Q> {
    val tail: AtomicRef<OneElementSegment<Q>>

    fun enqueue(element: Q) {
        val curTail = tail.value
        tail.findSegmentAndMoveForward(curTail, ::createSegment)
    }
}

private fun <C> createSegment(prev: OneElementSegment<C>?) = OneElementSegment(prev)

class OneElementSegment<O>(prev: OneElementSegment<O>?) : Segment<OneElementSegment<O>>(prev)

abstract class Segment<S : Segment<S>>(prev: S?)
