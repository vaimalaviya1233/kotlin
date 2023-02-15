// MODULE: common
// TARGET_PLATFORM: Common
class Foo

// MODULE: intermediate()()(common)
// TARGET_PLATFORM: Common
class Bar

// MODULE: main()()(intermediate)
expect class A
<!ACTUAL_WITHOUT_EXPECT!>actual class A<!>
