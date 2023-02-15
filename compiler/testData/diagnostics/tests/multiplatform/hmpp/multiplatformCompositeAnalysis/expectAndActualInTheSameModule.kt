// MODULE: common
// TARGET_PLATFORM: Common
expect <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class CommonClass<!>
actual <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class CommonClass<!>

expect fun <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>commonFun<!>()
actual fun <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>commonFun<!>() {}

expect val <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>commonProperty<!>: String
actual val <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>commonProperty<!>: String
    get() = "hello"

// MODULE: intermediate()()(common)
// TARGET_PLATFORM: Common
expect <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class IntermediateClass<!>
actual <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class IntermediateClass<!>

expect fun <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>intermediateFun<!>()
actual fun <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>intermediateFun<!>() {}

expect val <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>intermediateProperty<!>: String
actual val <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>intermediateProperty<!>: String
    get() = "hello"

// MODULE: main()()(intermediate)
expect <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class PlatformClass<!>
actual <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class PlatformClass<!>

expect fun <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>platformFun<!>()
actual fun <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>platformFun<!>() {}

expect val <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>platformProperty<!>: String
actual val <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>platformProperty<!>: String
    get() = "hello"
