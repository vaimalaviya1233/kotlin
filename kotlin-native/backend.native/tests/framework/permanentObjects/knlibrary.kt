@file:OptIn(kotlin.ExperimentalStdlibApi::class)

import kotlin.native.internal.GC
import kotlin.native.internal.gc.GCInfo
import kotlin.native.internal.isPermanent
import kotlin.test.*

private var _counter = 0

object Permanent {
    var counter
        get() = _counter
        set(value) {
            _counter = value
        }
}

fun assertIsPermanent() {
    assertTrue(Permanent.isPermanent())
}

fun stableRefsCount(): Long {
    GC.collect()
    val info = GC.lastGCInfo!!
    // Counting all stable refs in the root set + all registered foreign refs.
    return info.rootSet.stableReferences + info.foreignReferences
}
