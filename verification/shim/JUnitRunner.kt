/*
 * Axiom Calc - reflective JUnit-4-compatible runner (verification harness ONLY).
 *
 * Discovers compiled classes whose simple name ends with "Test", instantiates
 * them, and executes every function annotated with @org.junit.Test, wrapping
 * each in @Before / @After. Produces a deterministic, sorted report and a
 * process exit code of 0 only when every test passed.
 *
 * Honours:
 *   - @Test(expected = X::class)  : the test passes only if X is thrown.
 *   - @Ignore on a method or class: counted as skipped, never as failure.
 *
 * Not part of the Android application; never packaged.
 */

import java.io.File
import java.net.URLClassLoader
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

private data class TestOutcome(
    val className: String,
    val methodName: String,
    val status: Status,
    val detail: String = "",
) {
    enum class Status { PASSED, FAILED, ERROR, SKIPPED }
}

fun main(args: Array<String>) {
    val classesDir = File(args.getOrElse(0) { "." })
    val filter = args.getOrNull(1)

    val classNames = discover(classesDir)
    if (classNames.isEmpty()) {
        System.err.println("no test classes found under ${classesDir.absolutePath}")
        kotlin.system.exitProcess(2)
    }

    val loader = URLClassLoader(
        arrayOf(classesDir.toURI().toURL()),
        TestFailure::class.java.classLoader,
    )

    val outcomes = ArrayList<TestOutcome>()
    for (className in classNames) {
        if (filter != null && !className.contains(filter, ignoreCase = true)) continue
        runClass(loader, className, outcomes)
    }

    // ---- report ----
    val width = 100
    println("=".repeat(width))
    println("AXIOM CALC - STANDALONE VERIFICATION RUN (JUnit4-compatible shim harness)")
    println("classes dir : ${classesDir.absolutePath}")
    println("test classes: ${classNames.size}    executed: ${outcomes.count { it.status != TestOutcome.Status.SKIPPED }}")
    println("=".repeat(width))

    var currentClass = ""
    for (o in outcomes.sortedWith(compareBy({ it.className }, { it.methodName }))) {
        if (o.className != currentClass) {
            currentClass = o.className
            println(currentClass)
        }
        val mark = when (o.status) {
            TestOutcome.Status.PASSED -> "  PASS"
            TestOutcome.Status.FAILED -> "  FAIL"
            TestOutcome.Status.ERROR -> "  ERROR"
            TestOutcome.Status.SKIPPED -> "  SKIP"
        }
        val line = String.format("  %-8s %s", mark, o.methodName)
        println(if (o.detail.isEmpty()) line else "$line\n           -> ${o.detail}")
    }

    val passed = outcomes.count { it.status == TestOutcome.Status.PASSED }
    val failed = outcomes.count { it.status == TestOutcome.Status.FAILED }
    val errored = outcomes.count { it.status == TestOutcome.Status.ERROR }
    val skipped = outcomes.count { it.status == TestOutcome.Status.SKIPPED }
    println("-".repeat(width))
    println("TOTAL ${outcomes.size}   PASSED $passed   FAILED $failed   ERRORS $errored   SKIPPED $skipped")
    println("-".repeat(width))
    if (failed + errored > 0) {
        println("FAILURES AND ERRORS:")
        outcomes.filter { it.status == TestOutcome.Status.FAILED || it.status == TestOutcome.Status.ERROR }
            .forEach { println("  x ${it.className}.${it.methodName}: ${it.detail}") }
        println("RESULT: FAIL")
        kotlin.system.exitProcess(1)
    }
    println("RESULT: PASS")
    kotlin.system.exitProcess(0)
}

private class TestFailure

private fun discover(root: File): List<String> {
    val names = ArrayList<String>()
    root.walkTopDown()
        .filter { it.isFile && it.name.endsWith("Test.class") && !it.name.contains('$') }
        .forEach { file ->
            val rel = file.relativeTo(root).path
            val dotted = rel.removeSuffix(".class").replace(File.separatorChar, '.')
            names += dotted
        }
    return names.sorted()
}

private fun runClass(loader: ClassLoader, className: String, outcomes: MutableList<TestOutcome>) {
    val clazz: Class<*> = try {
        loader.loadClass(className)
    } catch (t: Throwable) {
        outcomes += TestOutcome(className, "<load>", TestOutcome.Status.ERROR, t.toString())
        return
    }
    val classIgnore = clazz.getAnnotation(Ignore::class.java)
    val before = clazz.methods.filter { it.isAnnotationPresent(Before::class.java) }
    val after = clazz.methods.filter { it.isAnnotationPresent(After::class.java) }
    val tests = clazz.methods.filter { it.isAnnotationPresent(Test::class.java) }
        .sortedBy { it.name }

    for (method in tests) {
        if (classIgnore != null || method.isAnnotationPresent(Ignore::class.java)) {
            outcomes += TestOutcome(className, method.name, TestOutcome.Status.SKIPPED,
                classIgnore?.value ?: method.getAnnotation(Ignore::class.java)?.value ?: "")
            continue
        }
        val instance = try {
            clazz.getDeclaredConstructor().newInstance()
        } catch (t: Throwable) {
            outcomes += TestOutcome(className, method.name, TestOutcome.Status.ERROR, "construction: $t")
            continue
        }
        val annotation = method.getAnnotation(Test::class.java)
        try {
            before.forEach { it.invoke(instance) }
            try {
                method.invoke(instance)
                if (annotation.expected != org.junit.Test.None::class) {
                    outcomes += TestOutcome(className, method.name, TestOutcome.Status.FAILED,
                        "expected ${annotation.expected} but nothing was thrown")
                } else {
                    outcomes += TestOutcome(className, method.name, TestOutcome.Status.PASSED)
                }
            } catch (invocation: java.lang.reflect.InvocationTargetException) {
                val cause = invocation.cause ?: invocation
                val expectedType = annotation.expected
                if (expectedType != org.junit.Test.None::class && expectedType.java.isInstance(cause)) {
                    outcomes += TestOutcome(className, method.name, TestOutcome.Status.PASSED)
                } else if (cause is AssertionError) {
                    outcomes += TestOutcome(className, method.name, TestOutcome.Status.FAILED, cause.message ?: "")
                } else {
                    outcomes += TestOutcome(className, method.name, TestOutcome.Status.ERROR,
                        "${cause::class.java.simpleName}: ${cause.message}")
                }
            } finally {
                after.forEach { runCatching { it.invoke(instance) } }
            }
        } catch (t: Throwable) {
            outcomes += TestOutcome(className, method.name, TestOutcome.Status.ERROR, t.toString())
        }
    }
}
