/*
 * ===========================================================================
 *  Axiom Calc - minimal JUnit 4 API shim (verification harness ONLY)
 * ===========================================================================
 *  Why this file exists
 *  --------------------
 *  The authoring sandbox cannot reach Maven Central, so the real
 *  `junit:junit:4.13.2` artifact cannot be downloaded. The unit tests in
 *  app/src/test are written against the *real* JUnit 4 API and run unchanged
 *  under `./gradlew test` on any normal machine.
 *
 *  To obtain executed test evidence inside the sandbox, this shim provides a
 *  source-compatible subset of the JUnit 4 API (annotations + Assert) and
 *  `JUnitRunner.kt` provides a reflective runner. The assertions performed are
 *  identical; only the harness differs. This is disclosed in TESTING.md and in
 *  the delivery report: results produced here are real executions of the real
 *  test classes, executed through a documented stand-in harness.
 *
 *  This file is NOT part of the Android application and is never packaged.
 */

package org.junit

/** Marks a function as a test case. Mirrors org.junit.Test. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test(
    val expected: kotlin.reflect.KClass<out Throwable> = None::class,
    val timeout: Long = 0L,
) {
    /** Sentinel meaning "no expected exception", mirroring Test.None. */
    class None : Throwable()
}

/** Runs before every test method. Mirrors org.junit.Before. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Before

/** Runs after every test method. Mirrors org.junit.After. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class After

/** Skips a test method with a reason. Mirrors org.junit.Ignore. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Ignore(val value: String = "")

/**
 * Source-compatible subset of org.junit.Assert.
 * Kotlin tests import members exactly as they would from real JUnit 4.
 */
object Assert {

    fun fail(message: String?): Nothing = throw AssertionError(message ?: "failed")

    fun assertTrue(message: String?, condition: Boolean) {
        if (!condition) throw AssertionError(message ?: "expected true but was false")
    }

    fun assertTrue(condition: Boolean) = assertTrue(null, condition)

    fun assertFalse(message: String?, condition: Boolean) {
        if (condition) throw AssertionError(message ?: "expected false but was true")
    }

    fun assertFalse(condition: Boolean) = assertFalse(null, condition)

    fun assertEquals(message: String?, expected: Any?, actual: Any?) {
        if (!objectsEqual(expected, actual)) {
            throw AssertionError(
                (message?.let { "$it " } ?: "") + "expected:<$expected> but was:<$actual>",
            )
        }
    }

    fun assertEquals(expected: Any?, actual: Any?) = assertEquals(null, expected, actual)

    fun assertEquals(message: String?, expected: Double, actual: Double, delta: Double) {
        if (expected.isNaN() && actual.isNaN()) return
        if ((expected - actual).let { if (it < 0) -it else it } > delta || expected.isNaN() || actual.isNaN()) {
            throw AssertionError(
                (message?.let { "$it " } ?: "") + "expected:<$expected> but was:<$actual> (delta $delta)",
            )
        }
    }

    fun assertEquals(expected: Double, actual: Double, delta: Double) =
        assertEquals(null, expected, actual, delta)

    fun assertNotEquals(message: String?, unexpected: Any?, actual: Any?) {
        if (objectsEqual(unexpected, actual)) {
            throw AssertionError((message?.let { "$it " } ?: "") + "values should differ but both were <$actual>")
        }
    }

    fun assertNotEquals(unexpected: Any?, actual: Any?) = assertNotEquals(null, unexpected, actual)

    fun assertNull(message: String?, value: Any?) {
        if (value != null) throw AssertionError((message?.let { "$it " } ?: "") + "expected null but was <$value>")
    }

    fun assertNull(value: Any?) = assertNull(null, value)

    fun assertNotNull(message: String?, value: Any?) {
        if (value == null) throw AssertionError(message ?: "expected non-null but was null")
    }

    fun assertNotNull(value: Any?) = assertNotNull(null, value)

    fun assertSame(expected: Any?, actual: Any?) {
        if (expected !== actual) throw AssertionError("expected same instance")
    }

    private fun objectsEqual(expected: Any?, actual: Any?): Boolean {
        if (expected is Double && actual is Double) return expected == actual
        if (expected is Float && actual is Float) return expected == actual
        return expected == actual
    }
}
