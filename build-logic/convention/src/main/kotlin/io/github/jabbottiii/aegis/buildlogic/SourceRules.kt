package io.github.jabbottiii.aegis.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Source rules that need no type resolution, so they hold even where detekt's type-resolved rules
 * can't run (detekt 1.23 has no Android variant tasks under AGP 9). They cover AGENTS.md
 * rule 8 (no direct logging) and "Conventions" (injected dispatchers, structured
 * concurrency, no `!!` in engine code). While editing, `.claude/hooks/post_edit_checks.py` warns
 * about a subset of these patterns in Kotlin files, over the same files. This task is the CI gate.
 *
 * It's a pattern scan over code with comments and string literals removed. It catches the listed
 * forms, including fully qualified calls, callable references, and calls split across lines. Code
 * that hides a call behind reflection or an alias of a non-logging name still needs review.
 */
internal object SourceRules {
    /** The logging facade's only Android adapter. */
    private val LOGCAT_SINK = Regex("""^app/src/main/kotlin/(.+/)?logging/LogcatSink\.kt$""")

    /** The DI root is the one place that names the platform dispatchers. */
    private val DISPATCHERS_MODULE = Regex("""^app/src/main/kotlin/(.+/)?di/DispatchersModule\.kt$""")

    /** Areas whose code decides verdicts or parses hostile data (kotlin-engine rule). */
    private val NO_BANG_BANG_PREFIXES =
        listOf("engine/", "shield/", "ai/", "intel/", "core/security/", "core/common/", "core/model/")

    /** Not a member call (`w.print(`), not part of a longer name, and not a declaration (`fun print(`). */
    private const val BARE = """(?<![\w.$])(?<!\bfun\s{1,16})"""

    private val LOGGING =
        listOf(
            Regex("""${BARE}print(ln)?\s*\(""") to "print/println",
            Regex("""(?<![\w.$)\]])::\s*print(ln)?\b""") to "::print/::println",
            Regex("""\bkotlin\s*\.\s*io\s*\.\s*print(ln)?\b""") to "kotlin.io.print/println",
            Regex("""\bSystem\s*\.\s*(out|err|console|getLogger)\b""") to "System.out/err/console/getLogger",
            Regex("""\bandroid\s*\.\s*util\s*\.\s*Log\b""") to "android.util.Log",
            Regex("""${BARE}Log\s*\.\s*(v|d|i|w|e|wtf|println)\s*\(""") to "Log.*",
            Regex("""\bprintStackTrace\b""") to "printStackTrace",
            Regex("""\bThread\s*\.\s*dumpStack\b""") to "Thread.dumpStack",
            Regex("""\bjava\s*\.\s*util\s*\.\s*logging\b""") to "java.util.logging",
        )
    private val DISPATCHERS = Regex("""\bDispatchers\s*\.\s*(IO|Default|Main|Unconfined)\b""")
    private val GLOBAL_SCOPE = Regex("""\bGlobalScope\b""")
    private val RUN_BLOCKING = Regex("""\brunBlocking\b""")
    private val BANG_BANG = Regex("""!!""")

    /** [relativePath] is relative to the repository root and uses `/`. */
    fun violations(
        relativePath: String,
        text: String,
    ): List<String> {
        val code = codeOnly(text, kotlin = !relativePath.endsWith(".java"))
        val found = mutableListOf<Pair<Int, String>>()
        fun check(
            rx: Regex,
            message: String,
        ) {
            rx.findAll(code).forEach { match ->
                val line = 1 + code.subSequence(0, match.range.first).count { it == '\n' }
                found += line to message
            }
        }

        if (!LOGCAT_SINK.matches(relativePath)) {
            LOGGING.forEach { (rx, what) ->
                check(rx, "$what is forbidden. Use the core:common logging facade (docs/privacy.md, Logging).")
            }
        }
        if (!DISPATCHERS_MODULE.matches(relativePath)) {
            check(DISPATCHERS, "Inject AegisDispatchers instead of naming Dispatchers.* directly.")
        }
        check(GLOBAL_SCOPE, "GlobalScope is forbidden. Use structured concurrency with an injected scope.")
        check(RUN_BLOCKING, "runBlocking is forbidden in main sources.")
        if (NO_BANG_BANG_PREFIXES.any { relativePath.startsWith(it) }) {
            check(BANG_BANG, "`!!` is forbidden in engine code. Handle absence explicitly.")
        }
        return found.sortedBy { it.first }.map { (line, message) -> "$relativePath:$line: $message" }.distinct()
    }

    /**
     * Blanks out comments and string literals and keeps line breaks, so rules match code only.
     * Expressions inside Kotlin `${...}` templates stay as code. Kotlin block comments nest, Java
     * block comments don't.
     */
    internal fun codeOnly(
        text: String,
        kotlin: Boolean = true,
    ): String = Lexer(text, kotlin).run()

    private class Lexer(
        private val text: String,
        private val kotlin: Boolean,
    ) {
        private val out = StringBuilder(text.length)
        private var i = 0

        /** Open `${` templates. Each entry counts the `{` still open inside that template's code. */
        private val templates = ArrayDeque<Int>()

        fun run(): String {
            code()
            return out.toString()
        }

        private fun keep() {
            out.append(text[i++])
        }

        private fun blank() {
            out.append(if (text[i] == '\n') '\n' else ' ')
            i++
        }

        private fun blank(count: Int) = repeat(count) { if (i < text.length) blank() }

        private fun at(token: String) = text.startsWith(token, i)

        /** Code until end of input, or until the `}` that closes the innermost template. */
        private fun code() {
            while (i < text.length) {
                when {
                    at("//") -> lineComment()
                    at("/*") -> blockComment()
                    at("\"\"\"") -> rawString()
                    text[i] == '"' -> string()
                    text[i] == '\'' -> charLiteral()
                    text[i] == '{' && templates.isNotEmpty() -> {
                        templates.addLast(templates.removeLast() + 1)
                        keep()
                    }
                    text[i] == '}' && templates.isNotEmpty() -> {
                        val open = templates.removeLast()
                        if (open == 0) {
                            blank()
                            return
                        }
                        templates.addLast(open - 1)
                        keep()
                    }
                    else -> keep()
                }
            }
        }

        private fun lineComment() {
            while (i < text.length && text[i] != '\n') blank()
        }

        private fun blockComment() {
            var depth = 0
            while (i < text.length) {
                when {
                    at("/*") -> {
                        depth++
                        blank(2)
                    }
                    at("*/") -> {
                        depth--
                        blank(2)
                        if (depth == 0 || !kotlin) return
                    }
                    else -> blank()
                }
            }
        }

        private fun string() {
            blank()
            while (i < text.length && text[i] != '"' && text[i] != '\n') {
                when {
                    text[i] == '\\' -> blank(2)
                    kotlin && at("\${") -> template()
                    else -> blank()
                }
            }
            if (i < text.length) blank()
        }

        private fun rawString() {
            blank(3)
            while (i < text.length) {
                when {
                    at("\"\"\"") -> {
                        // The string ends at the last three quotes of a run, so `""""` ends with a quote.
                        var run = 0
                        while (i + run < text.length && text[i + run] == '"') run++
                        blank(run)
                        return
                    }
                    kotlin && at("\${") -> template()
                    else -> blank()
                }
            }
        }

        private fun template() {
            blank(2)
            templates.addLast(0)
            code()
        }

        private fun charLiteral() {
            blank()
            while (i < text.length && text[i] != '\'' && text[i] != '\n') {
                if (text[i] == '\\') blank(2) else blank()
            }
            if (i < text.length) blank()
        }
    }
}

/** `./gradlew checkSourceRules`: applies [SourceRules] to every production source file. */
abstract class CheckSourceRulesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val rootPath: Property<String>

    @TaskAction
    fun check() {
        val root = File(rootPath.get())
        val found =
            sources.files.sorted().flatMap { file ->
                SourceRules.violations(file.relativeTo(root).invariantSeparatorsPath, file.readText())
            }
        if (found.isNotEmpty()) {
            throw GradleException("Source rule violations:\n" + found.joinToString("\n") { "  - $it" })
        }
        logger.lifecycle("Source rules OK (${sources.files.size} files).")
    }
}
