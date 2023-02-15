// MODULE: common
// TARGET_PLATFORM: Common
class Foo

// MODULE: intermediate()()(common)
// TARGET_PLATFORM: Common
expect <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class A<!>
actual <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class A<!>

// MODULE: main()()(intermediate)
class Bar