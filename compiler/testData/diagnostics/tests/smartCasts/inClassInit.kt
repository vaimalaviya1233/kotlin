// DIAGNOSTICS: -DEBUG_INFO_LEAKING_THIS

open class Base {
    val a: Int
    var b: Int
    open val c: Int
    open val d: Int
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open val e: Int<!>
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open var f: Int<!>

    val a2: Int
    var b2: Int
    open val c2: Int
    open val d2: Int
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open val e2: Int<!>
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open var f2: Int<!>

    val a3: Int
    var b3: Int
    open val c3: Int
    open val d3: Int
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open val e3: Int<!>
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open var f3: Int<!>

    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>val a4: Int<!>
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>var b4: Int<!>
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open val c4: Int<!>
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open val d4: Int<!>
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open val e4: Int<!>
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open var f4: Int<!>

    init {
        if (this is Derrived) {
            this.a = 1
            this.b = 1
            this.c = 1
            this.d = 1
            <!DEBUG_INFO_SMARTCAST!>this<!>.e = 1
            this.f = 1

            Base().run {
                this@Base.a2 = 1
                this@Base.b2 = 1
                this@Base.c2 = 1
                this@Base.d2 = 1
                <!DEBUG_INFO_SMARTCAST!>this@Base<!>.e2 = 1
                this@Base.f2 = 1
            }

            Base().run Base@ {
                this<!LABEL_RESOLVE_WILL_CHANGE!>@Base<!>.a3 = 1
                this<!LABEL_RESOLVE_WILL_CHANGE!>@Base<!>.b3 = 1
                this<!LABEL_RESOLVE_WILL_CHANGE!>@Base<!>.c3 = 1
                this<!LABEL_RESOLVE_WILL_CHANGE!>@Base<!>.d3 = 1
                <!DEBUG_INFO_SMARTCAST!>this<!LABEL_RESOLVE_WILL_CHANGE!>@Base<!><!>.e3 = 1
                this<!LABEL_RESOLVE_WILL_CHANGE!>@Base<!>.f3 = 1
            }

            Base().run {
                <!VAL_REASSIGNMENT!>this.a4<!> = 1
                this.b4 = 1
                <!VAL_REASSIGNMENT!>this.c4<!> = 1
                <!VAL_REASSIGNMENT!>this.d4<!> = 1
                <!VAL_REASSIGNMENT!>this.e4<!> = 1
                this.f4 = 1
            }
        } else {
            throw Exception()
        }
    }

    fun foo() {
        if (this is Derrived) {
            <!VAL_REASSIGNMENT!>this.a<!> = 1
            this.b = 1
            <!VAL_REASSIGNMENT!>this.c<!> = 1
            <!VAL_REASSIGNMENT!>this.d<!> = 1
            <!DEBUG_INFO_SMARTCAST!>this<!>.e = 1
            this.f = 1
        }
    }
}

class Derrived : Base() {
    override val c get() = 2
    override val d = 2
    override var e = 2
    override var f = 2

    override val c2 get() = 2
    override val d2 = 2
    override var e2 = 2
    override var f2 = 2

    override val c3 get() = 2
    override val d3 = 2
    override var e3 = 2
    override var f3 = 2

    override val c4 get() = 2
    override val d4 = 2
    override var e4 = 2
    override var f4 = 2
}
