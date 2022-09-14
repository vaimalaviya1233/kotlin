@JvmInline
value class Some(val value: String)

var topLevelProp: Some = Some("1")
var Some.topLevelPropInExtension: Int
    get() = 1
    set(value) {}

fun topLevelFunInReturn(): Some = Some("1")
fun topLevelFunInParameter(s: Some) {}
fun Some.topLevelFunInExtension() {}
