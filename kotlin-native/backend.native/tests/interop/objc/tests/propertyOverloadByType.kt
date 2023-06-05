import kotlinx.cinterop.*
import kotlin.test.*
import objcTests.*

@Test fun testPropertyOverloadByType() {
    val base = InterfaceBase()
    val base2 = InterfaceBase(base)  // Terminating app due to uncaught exception 'NSInvalidArgumentException', reason: '-[InterfaceBase copyWithZone:]: unrecognized selector sent to instance 0x600001458130'
    val derived = InterfaceDerived()
    val derived2 = InterfaceDerived(derived)

    val delegate1_InterfaceBase: InterfaceBase? = base2.delegate
    assertEquals(base, delegate1_InterfaceBase)
    val delegate2_InterfaceBase: InterfaceBase? = base2.delegate()
    assertEquals(base, delegate2_InterfaceBase)

    val delegate3_InterfaceBase: InterfaceBase? = derived2.delegate
    assertEquals(derived, delegate3_InterfaceBase)
    val delegate4_Long: Long = derived2.delegate()
    assertEquals(0L, delegate4_Long)

    assertTrue(delegate4_Long is InterfaceBase)
    assertEquals<Any?>(derived as InterfaceBase, delegate4_Long as Long)

    assertEquals(base as InterfaceBase, delegate4_Long as InterfaceBase)  // K1 warning for `delegate as InterfaceBase`: warning: this cast can never succeed
    assertEquals(base, delegate4_Long)
}
