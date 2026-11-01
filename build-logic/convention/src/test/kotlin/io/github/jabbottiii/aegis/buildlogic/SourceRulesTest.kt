package io.github.jabbottiii.aegis.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceRulesTest {
    private fun violations(
        path: String,
        code: String,
    ) = SourceRules.violations(path, code.trimIndent())

    private fun lines(found: List<String>) = found.map { it.substringBefore(": ").substringAfterLast(':').toInt() }

    private val app = "app/src/main/kotlin/io/github/x/aegis/ui/Screen.kt"
    private val engine = "engine/apk/src/main/kotlin/io/github/x/aegis/engine/apk/Zip.kt"
    private val q = "\"\"\""

    @Test
    fun `flags every direct logging form`() {
        val found =
            violations(
                app,
                """
                fun a() = println(url)
                fun b() = print(url)
                fun c() = kotlin.io.println(url)
                fun d() = System.out.write(1)
                fun e() = System.err.flush()
                fun f() = android.util.Log.w("t", url)
                fun g() = Log.e("t", url)
                fun h(t: Throwable) = t.printStackTrace()
                fun i() = Thread.dumpStack()
                fun j() = System.getLogger("x")
                fun k() = java.util.logging.Logger.getLogger("x")
                """,
            )
        assertEquals((1..11).toList(), lines(found))
    }

    @Test
    fun `flags callable references to print functions`() {
        val found =
            violations(
                app,
                """
                fun a(xs: List<String>) = xs.forEach(::println)
                fun b(t: Throwable) = t.let(Throwable::printStackTrace)
                fun ok(xs: List<String>, w: java.io.PrintWriter) = xs.forEach(w::println)
                """,
            )
        assertEquals(listOf(1, 2), lines(found))
    }

    @Test
    fun `flags imports of logging APIs, aliased or not`() {
        val found = violations(app, "import android.util.Log as L\nimport java.util.logging.Logger\n")
        assertEquals(listOf(1, 2), lines(found))
    }

    @Test
    fun `flags calls split across lines`() {
        val found = violations(app, "fun a() = Log\n    .w(\"t\", url)\nval io = Dispatchers\n    .IO\n")
        assertEquals(listOf(1, 3), lines(found))
    }

    @Test
    fun `flags calls inside string templates`() {
        val found = violations(app, "val s = \"value: \${println(x)}\"\nval r = $q\${Log.w(\"t\", u)}$q\n")
        assertEquals(listOf(1, 2), lines(found))
    }

    @Test
    fun `ignores logging words inside strings, comments, and declarations`() {
        val found =
            violations(
                app,
                """
                // println(url) in a comment
                /* Log.w("t", url)
                   System.out */
                /* outer /* nested */ println(still a comment) */
                val s = "println(url) and android.util.Log"
                val t = "braces { in } strings: ${'$'}{x.size} then println("
                val raw = ${q}Log.e("t", x)$q
                val c = '"'
                fun ok(w: java.io.PrintWriter) = w.println("x")
                fun fingerprint(a: Int) = a
                override fun println(x: Any?) = Unit
                """,
            )
        assertTrue(found.isEmpty(), found.toString())
    }

    @Test
    fun `a raw string that ends in a quote doesn't hide the code after it`() {
        val found = violations(app, "val s = $q\"quoted\"$q; println(s)\n")
        assertEquals(listOf(1), lines(found))
    }

    @Test
    fun `Java block comments don't nest`() {
        val java = "app/src/main/java/io/github/x/aegis/A.java"
        val found = violations(java, "/* a /* b */ System.out.println(x);\n")
        assertEquals(listOf(1), lines(found))
    }

    @Test
    fun `keeps line numbers after a multi-line comment`() {
        assertEquals(listOf(4), lines(violations(app, "/*\n\n*/\nfun a() = println(x)\n")))
    }

    @Test
    fun `exempts only the logcat sink from logging rules`() {
        val sink = "app/src/main/kotlin/io/github/x/aegis/logging/LogcatSink.kt"
        val log = "fun w() = Log.w(TAG, line)\n"
        assertTrue(violations(sink, "import android.util.Log\n$log").isEmpty())
        assertEquals(1, violations(app.replace("ui/Screen", "logging/Other"), log).size)
        assertEquals(1, violations(sink.replace("LogcatSink.kt", "LogcatSinkExtra.kt"), log).size)
        assertEquals(1, violations("feature/$sink", log).size)
    }

    @Test
    fun `flags named dispatchers outside the DI module`() {
        val di = "app/src/main/kotlin/io/github/x/aegis/di/DispatchersModule.kt"
        assertTrue(violations(di, "val io = Dispatchers.IO\n").isEmpty())
        assertEquals(1, violations(engine, "withContext(Dispatchers.IO) { }\n").size)
        assertEquals(1, violations(app, "val d = Dispatchers.Default\n").size)
    }

    @Test
    fun `flags GlobalScope and runBlocking`() {
        assertEquals(2, violations(app, "fun a() = GlobalScope.launch { }\nfun b() = runBlocking { }\n").size)
    }

    @Test
    fun `flags not-null assertions only in engine and core areas`() {
        assertEquals(1, violations(engine, "val x = y!!\n").size)
        assertEquals(1, violations("core/common/src/main/kotlin/A.kt", "val x = y!!.z\n").size)
        assertTrue(violations(app, "val x = y!!\n").isEmpty())
        assertTrue(violations(engine, "val s = \"wow!!\"\nval ok = a != b\n").isEmpty())
    }
}
