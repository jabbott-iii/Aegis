package io.github.jabbottiii.aegis.buildlogic

import io.github.jabbottiii.aegis.buildlogic.GraphNode.ModuleNode
import io.github.jabbottiii.aegis.buildlogic.GraphNode.ProjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleGraphRulesTest {
    private val android = setOf(":app", ":core:ui", ":core:security", ":feature:scan", ":feature:shield")
    private val tink = ModuleNode("com.google.crypto.tink", "tink-android")
    private val okhttp = ModuleNode("com.squareup.okhttp3", "okhttp")

    private fun graph(
        vararg roots: GraphNode,
        edges: Map<GraphNode, List<GraphNode>> = emptyMap(),
    ) = DependencyGraph(roots.toList(), edges)

    private fun violations(
        path: String,
        classpath: String,
        graph: DependencyGraph,
    ) = ModuleGraphRules.resolvedViolations(path, classpath, graph, android)

    @Test
    fun `a pure module can't reach Android libraries or modules, even transitively`() {
        val lib = ModuleNode("org.example", "lib")
        val g =
            graph(
                lib,
                ProjectNode(":core:ui"),
                edges = mapOf(lib to listOf(ModuleNode("com.google.android", "android"))),
            )
        val found = violations(":core:model", "runtimeClasspath", g)
        assertEquals(2, found.size, found.toString())
    }

    @Test
    fun `Android groups match exactly or by dotted prefix`() {
        assertTrue(ModuleGraphRules.isAndroidGroup("com.google.android"))
        assertTrue(ModuleGraphRules.isAndroidGroup("androidx.core"))
        assertTrue(ModuleGraphRules.isAndroidGroup("org.robolectric"))
        assertFalse(ModuleGraphRules.isAndroidGroup("com.androidplot"))
        assertFalse(ModuleGraphRules.isAndroidGroup("androidxyz"))
    }

    @Test
    fun `reaching Tink at runtime through core security is allowed`() {
        val security = ProjectNode(":core:security")
        val g = graph(security, edges = mapOf(security to listOf(tink)))
        assertTrue(violations(":feature:scan", "releaseRuntimeClasspath", g).isEmpty())
    }

    @Test
    fun `Tink on a consumer's compile classpath is an api leak`() {
        val security = ProjectNode(":core:security")
        val g = graph(security, edges = mapOf(security to listOf(tink)))
        assertEquals(1, violations(":feature:scan", "releaseCompileClasspath", g).size)
    }

    @Test
    fun `restricted groups reached through a third-party library are flagged`() {
        val sdk = ModuleNode("org.example", "sdk")
        val g = graph(sdk, edges = mapOf(sdk to listOf(okhttp)))
        assertEquals(1, violations(":engine:scanner", "debugRuntimeClasspath", g).size)
        assertTrue(violations(":intel:client", "debugRuntimeClasspath", g).isEmpty())
    }

    @Test
    fun `feature modules can't reach each other transitively`() {
        val ui = ProjectNode(":core:ui")
        val g = graph(ui, edges = mapOf(ui to listOf(ProjectNode(":feature:shield"))))
        assertEquals(1, violations(":feature:scan", "releaseRuntimeClasspath", g).size)
        assertTrue(violations(":app", "releaseRuntimeClasspath", g).isEmpty())
    }

    @Test
    fun `walk visits each node once and survives cycles`() {
        val a = ModuleNode("g", "a")
        val b = ModuleNode("g", "b")
        val visited = mutableListOf<GraphNode>()
        graph(a, edges = mapOf(a to listOf(b), b to listOf(a))).walk { visited += it }
        assertEquals(listOf(a, b), visited)
    }

    @Test
    fun `only production compile and runtime classpaths are checked`() {
        val checked =
            listOf(
                "compileClasspath",
                "runtimeClasspath",
                "debugCompileClasspath",
                "releaseRuntimeClasspath",
                "fossReleaseRuntimeClasspath",
            )
        val skipped =
            listOf(
                "testCompileClasspath",
                "debugUnitTestRuntimeClasspath",
                "debugAndroidTestCompileClasspath",
                "testFixturesRuntimeClasspath",
                "kspDebugKotlinProcessorClasspath",
                "detekt",
            )
        assertTrue(checked.all(ModuleGraphRules::isCheckedClasspath))
        assertTrue(skipped.none(ModuleGraphRules::isCheckedClasspath))
    }
}
