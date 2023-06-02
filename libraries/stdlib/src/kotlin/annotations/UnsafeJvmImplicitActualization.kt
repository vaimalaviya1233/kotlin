package kotlin

/**
 * This annotation marks Kotlin `expect` declarations that are implicitly actualized by Java.
 *
 * See: [KT-58545](https://youtrack.jetbrains.com/issue/KT-58545)
 */
@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@SinceKotlin("1.9")
public annotation class UnsafeJvmImplicitActualization
