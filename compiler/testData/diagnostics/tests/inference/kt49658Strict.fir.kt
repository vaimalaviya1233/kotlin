// !LANGUAGE: +DontLoseDiagnosticsDuringOverloadResolutionByReturnType
// WITH_STDLIB

fun doTheMapThing1(elements: List<CharSequence>): List<String> {
    return elements.<!OVERLOAD_RESOLUTION_AMBIGUITY!>flatMap<!> {
        when (it) { // NullPointerException
            is String -> listOf("Yeah")
            else -> null
        }
    }
}

fun doTheMapThing2(elements: List<CharSequence>): List<String> {
    return elements.<!OVERLOAD_RESOLUTION_AMBIGUITY!>flatMap<!> {
        if (it is String) listOf("Yeah") else null // it's OK with `if`
    }
}
