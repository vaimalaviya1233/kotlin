// WITH_STDLIB

// MODULE: m1-common
// FILE: common.kt

<!NO_ACTUAL_FOR_EXPECT{JVM}!>@OptIn(kotlin.UnsafeJvmImplicitActualization::class)
expect class <!PACKAGE_OR_CLASSIFIER_REDECLARATION!>Foo<!>() {
    <!NO_ACTUAL_FOR_EXPECT{JVM}!>fun foo()<!>
}<!>

// MODULE: m2-jvm()()(m1-common)
// FILE: Foo.java

public class Foo {
    public void foo() {
    }
}
