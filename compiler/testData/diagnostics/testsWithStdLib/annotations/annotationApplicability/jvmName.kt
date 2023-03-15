// !DIAGNOSTICS: -UNUSED_VARIABLE -UNUSED_PARAMETER

interface Intf {
    <!INAPPLICABLE_JVM_NAME!>@get:JvmName("getBar")<!>
    <!INAPPLICABLE_JVM_NAME!>@set:JvmName("setBar")<!>
    var foo: Int

    val getter: Int
        @get:JvmName("Intf_getter") get

    var setter: Int
        <!INAPPLICABLE_TARGET_ON_PROPERTY!>@set:JvmName("Intf_getter")<!> set // KT-15470
}
