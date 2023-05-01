open class Foo  {
    //                                                                 no setter;                                                                 setter with field;                                                                       setter with empty body;                                                        setter no field;
    // no getter
                                                                          var a00: Int;                                <!MUST_BE_INITIALIZED!>var a01: Int<!>; set(v) { field = v };                                                               var a02: Int; set;                              <!MUST_BE_INITIALIZED!>var a03: Int<!>; set(v) {};
                                                                          var c00: Int = 1;                                                   var c01: Int = 1; set(v) { field = v };                                                              var c02: Int = 1; set;                                                 var c03: Int = 1; set(v) {};
                               <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open var b00: Int<!>;                        <!MUST_BE_INITIALIZED!>open var b01: Int<!>; set(v) { field = v };                    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open var b02: Int<!>; set;                      <!MUST_BE_INITIALIZED!>open var b03: Int<!>; set(v) {};
                                                                     open var d00: Int = 1;                                              open var d01: Int = 1; set(v) { field = v };                                                         open var d02: Int = 1; set;                                            open var d03: Int = 1; set(v) {};
    // getter with field
                                                                          var a10: Int; get() = field;                 <!MUST_BE_INITIALIZED!>var a11: Int<!>; set(v) { field = v } get() = field;                                                 var a12: Int; set get() = field;                <!MUST_BE_INITIALIZED!>var a13: Int<!>; set(v) {} get() = field;
                                                                          var c10: Int = 1; get() = field;                                    var c11: Int = 1; set(v) { field = v } get() = field;                                                var c12: Int = 1; set get() = field;                                   var c13: Int = 1; set(v) {} get() = field;
                                              <!MUST_BE_INITIALIZED!>open var b10: Int<!>; get() = field;         <!MUST_BE_INITIALIZED!>open var b11: Int<!>; set(v) { field = v } get() = field;                     <!MUST_BE_INITIALIZED!>open var b12: Int<!>; set get() = field;        <!MUST_BE_INITIALIZED!>open var b13: Int<!>; set(v) {} get() = field;
                                                                     open var d10: Int = 1; get() = field;                               open var d11: Int = 1; set(v) { field = v } get() = field;                                           open var d12: Int = 1; set get() = field;                              open var d13: Int = 1; set(v) {} get() = field;
    // getter with empty body
                                                                          var a20: Int; get;                           <!MUST_BE_INITIALIZED!>var a21: Int<!>; set(v) { field = v } get;                                                           var a22: Int; set get;                          <!MUST_BE_INITIALIZED!>var a23: Int<!>; set(v) {} get;
                                                                          var c20: Int = 1; get;                                              var c21: Int = 1; set(v) { field = v } get;                                                          var c22: Int = 1; set get;                                             var c23: Int = 1; set(v) {} get;
                               <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open var b20: Int<!>; get;                   <!MUST_BE_INITIALIZED!>open var b21: Int<!>; set(v) { field = v } get;                <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>open var b22: Int<!>; set get;                  <!MUST_BE_INITIALIZED!>open var b23: Int<!>; set(v) {} get;
                                                                     open var d20: Int = 1; get;                                         open var d21: Int = 1; set(v) { field = v } get;                                                     open var d22: Int = 1; set get;                                        open var d23: Int = 1; set(v) {} get;
    // getter no field
                                                                          var a30: Int; get() = 1;                     <!MUST_BE_INITIALIZED!>var a31: Int<!>; set(v) { field = v } get() = 1;                                                     var a32: Int; set get() = 1;                                           var a33: Int; set(v) {} get() = 1;
                                                                          var c30: Int = 1; get() = 1;                                        var c31: Int = 1; set(v) { field = v } get() = 1;                                                    var c32: Int = 1; set get() = 1;                                       var c33: Int = <!PROPERTY_INITIALIZER_NO_BACKING_FIELD!>1<!>; set(v) {} get() = 1;
                                              <!MUST_BE_INITIALIZED!>open var b30: Int<!>; get() = 1;             <!MUST_BE_INITIALIZED!>open var b31: Int<!>; set(v) { field = v } get() = 1;                         <!MUST_BE_INITIALIZED!>open var b32: Int<!>; set get() = 1;                                   open var b33: Int; set(v) {} get() = 1;
                                                                     open var d30: Int = 1; get() = 1;                                   open var d31: Int = 1; set(v) { field = v } get() = 1;                                               open var d32: Int = 1; set get() = 1;                                  open var d33: Int = <!PROPERTY_INITIALIZER_NO_BACKING_FIELD!>1<!>; set(v) {} get() = 1;

    init {
        a00 = 1
        <!DEBUG_INFO_LEAKING_THIS!>a01<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>a02<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>a03<!> = 1
        a10 = 1
        <!DEBUG_INFO_LEAKING_THIS!>a11<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>a12<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>a13<!> = 1
        a20 = 1
        <!DEBUG_INFO_LEAKING_THIS!>a21<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>a22<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>a23<!> = 1
        a30 = 1
        <!DEBUG_INFO_LEAKING_THIS!>a31<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>a32<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>a33<!> = 1

        <!DEBUG_INFO_LEAKING_THIS!>b00<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b01<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b02<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b03<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b10<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b11<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b12<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b13<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b20<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b21<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b22<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b23<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b30<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b31<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b32<!> = 1
        <!DEBUG_INFO_LEAKING_THIS!>b33<!> = 1
    }
}
