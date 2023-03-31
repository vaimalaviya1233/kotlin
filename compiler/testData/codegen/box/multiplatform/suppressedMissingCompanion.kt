// !LANGUAGE: +MultiPlatformProjects
// TARGET_BACKEND: JVM

// MODULE: common
// TARGET_PLATFORM: Common
// FILE: common.kt
expect class Foo() {
    companion object {
        fun o(): String
        fun k(): String
    }
}

fun o(): String {
    return Foo.o()
}

val k = Foo::k

expect class Baz {
    class Qux {
        fun quux()
    }
}

// MODULE: main()()(common)
// TARGET_PLATFORM: JVM
// FILE: JFoo.java
public class JFoo {
    public static String o() {
        return "O";
    }
    public static String k() {
        return "K";
    }
}

// FILE: jvm2.kt
@Suppress("NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS")
actual typealias Foo = JFoo

@Suppress("NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS")
actual class Baz

fun box() = o() + k()
