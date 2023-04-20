// FILE: test.kt

fun box() {
    foo({
            val a = 1
        })

    foo() {
        val a = 1
    }
}

fun foo(f: () -> Unit) {
    f()
}

// EXPECTATIONS JVM JVM_IR
// test.kt:4 box
// test.kt:14 foo
// EXPECTATIONS JVM
// test.kt:5 invoke
// test.kt:6 invoke
// EXPECTATIONS JVM_IR
// test.kt:5 box$lambda$0
// test.kt:6 box$lambda$0
// EXPECTATIONS JVM JVM_IR
// test.kt:14 foo
// test.kt:15 foo
// EXPECTATIONS JVM_IR
// test.kt:4 box
// EXPECTATIONS JVM JVM_IR
// test.kt:8 box
// test.kt:14 foo
// EXPECTATIONS JVM
// test.kt:9 invoke
// test.kt:10 invoke
// EXPECTATIONS JVM_IR
// test.kt:9 box$lambda$1
// test.kt:10 box$lambda$1
// EXPECTATIONS JVM JVM_IR
// test.kt:14 foo
// test.kt:15 foo
// test.kt:11 box

// EXPECTATIONS JS_IR
// test.kt:4 box
// test.kt:14 foo
// test.kt:5 box$lambda
// test.kt:6 box$lambda
// test.kt:15 foo
// test.kt:8 box
// test.kt:14 foo
// test.kt:9 box$lambda
// test.kt:10 box$lambda
// test.kt:15 foo
// test.kt:11 box
