// TARGET_BACKEND: JVM
// TARGET_BACKEND: JVM_IR
// ALLOW_KOTLIN_PACKAGE
// WITH_STDLIB
// WORKS_WHEN_VALUE_CLASS
// LANGUAGE: +ValueClasses, +GenericInlineClassParameter

// FILE: result.kt
package kotlin

OPTIONAL_JVM_INLINE_ANNOTATION
value class Result<T: Any>(val value: T?)

// FILE: box.kt

fun box(): String {
    return Result("OK").value!!
}
