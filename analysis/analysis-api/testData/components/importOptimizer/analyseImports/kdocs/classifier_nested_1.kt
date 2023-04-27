// FILE: main.kt
package test

import dependency.MyClass
import dependency.MyClass.Nested

/**
 * [Nested]
 */
fun usage() {}

// FILE: dependency.kt
package dependency

class MyClass {
    class Nested
}
