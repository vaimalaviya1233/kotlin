// !LANGUAGE: +MultiPlatformProjects
// TARGET_BACKEND: JVM

// MODULE: common
// TARGET_PLATFORM: Common
// FILE: common.kt
expect class Foo() {
    companion object {
        fun bar(): String
    }
}

fun bar(): String {
    return Foo.bar()
}

expect class Baz {
    class Qux {
        fun quux()
    }
}

// MODULE: main()()(common)
// TARGET_PLATFORM: JVM
// FILE: JFoo.java
public class JFoo {
    public static String baz() {
        return "OK";
    }
}

// FILE: jvm2.kt
@Suppress("NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS")
actual typealias Foo = JFoo

@Suppress("NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS")
actual class Baz

fun box() = bar()
