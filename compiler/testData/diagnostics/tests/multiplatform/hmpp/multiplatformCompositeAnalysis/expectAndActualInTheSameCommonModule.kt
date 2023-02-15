// MODULE: common
// TARGET_PLATFORM: Common
expect <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class A<!>
actual <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class A<!>

// MODULE: intermediate()()(common)
// TARGET_PLATFORM: Common
class Foo

// MODULE: main()()(intermediate)
class Bar
