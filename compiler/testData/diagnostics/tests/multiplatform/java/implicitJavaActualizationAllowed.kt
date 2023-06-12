// WITH_STDLIB

// MODULE: m1-common
// FILE: common.kt

import kotlin.jvm.UnsafeJvmImplicitActualization

@OptIn(UnsafeJvmImplicitActualization::class)
expect class Foo() {
    fun foo()
}

// MODULE: m2-jvm()()(m1-common)
// FILE: Foo.java

public class Foo {
    public void foo() {
    }
}
