// FIR_BLOCKED: LC don't support DefaultImpls
// !JVM_DEFAULT_MODE: all

interface Foo {
    fun foo() {
        System.out.println("foo")
    }

    fun foo2(a: Int) {
        System.out.println("foo2")
    }

    fun bar()
}
