// MODULE: common
// TARGET_PLATFORM: Common
class Foo

// MODULE: intermediate()()(common)
// TARGET_PLATFORM: Common
expect class A
<!ACTUAL_WITHOUT_EXPECT!>actual class A<!>

// MODULE: main()()(intermediate)
class Bar