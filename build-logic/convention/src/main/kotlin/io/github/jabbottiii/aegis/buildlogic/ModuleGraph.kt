package io.github.jabbottiii.aegis.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** A node in a resolved dependency graph, reduced to what the rules need. */
internal sealed interface GraphNode {
    data class ProjectNode(
        val path: String,
    ) : GraphNode

    data class ModuleNode(
        val group: String,
        val module: String,
    ) : GraphNode
}

/** A resolved classpath: the root module's direct dependencies, plus every node's dependencies. */
internal class DependencyGraph(
    val roots: List<GraphNode>,
    val edges: Map<GraphNode, List<GraphNode>>,
) {
    /** Visits each reachable node once. Doesn't descend past a node for which [stopAt] is true. */
    fun walk(
        stopAt: (GraphNode) -> Boolean = { false },
        visit: (GraphNode) -> Unit,
    ) {
        val seen = mutableSetOf<GraphNode>()
        val queue = ArrayDeque(roots.filter { seen.add(it) })
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            visit(node)
            if (!stopAt(node)) edges[node].orEmpty().filter { seen.add(it) }.forEach { queue.addLast(it) }
        }
    }

    companion object {
        fun from(root: ResolvedComponentResult): DependencyGraph {
            fun ResolvedComponentResult.node(): GraphNode? =
                when (val id = id) {
                    is ProjectComponentIdentifier -> GraphNode.ProjectNode(id.projectPath)
                    is ModuleComponentIdentifier -> GraphNode.ModuleNode(id.group, id.module)
                    else -> null
                }

            fun ResolvedComponentResult.children(): List<ResolvedComponentResult> =
                dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected }

            val edges = mutableMapOf<GraphNode, List<GraphNode>>()
            val seen = mutableSetOf(root.id)
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty()) {
                val component = queue.removeFirst()
                val node = component.node()
                val children = component.children()
                if (node != null && component !== root) edges[node] = children.mapNotNull { it.node() }
                children.filter { seen.add(it.id) }.forEach { queue.addLast(it) }
            }
            return DependencyGraph(root.children().mapNotNull { it.node() }, edges)
        }
    }
}

/**
 * Module dependency rules from docs/01-architecture.md ("Module layout") and AGENTS.md
 * ("Architecture rules"). The check runs in CI as `./gradlew checkModuleGraph`. It inspects
 * declared dependencies and also the resolved production classpaths, so a rule can't be bypassed
 * through a transitive dependency.
 */
internal object ModuleGraphRules {
    /** Pure Kotlin/JVM modules (AGENTS.md, "Architecture rules"): no Android framework at all. */
    val PURE_MODULES: Set<String> =
        setOf(
            ":engine:apk",
            ":engine:signatures",
            ":engine:heuristics",
            ":shield:dns",
            ":core:model",
            ":core:common",
        )

    /** Library groups that only the listed modules may use, directly or transitively, outside tests. */
    val RESTRICTED_GROUPS: Map<String, Set<String>> =
        mapOf(
            // Only core:security touches the Keystore and Tink.
            "com.google.crypto.tink" to setOf(":core:security"),
            // Only intel:client does network I/O for bundles. shield:dns owns the DoH upstream.
            "com.squareup.okhttp3" to setOf(":intel:client", ":shield:dns"),
        )

    private val ANDROID_GROUPS = listOf("androidx", "com.android", "com.google.android", "org.robolectric")

    /** Configurations that hold declared dependencies. AGP and Hilt add file dependencies to their own. */
    private val DECLARED_BUCKET_SUFFIX =
        Regex("""(^|[a-z])(api|Api|implementation|Implementation|compileOnly|CompileOnly|runtimeOnly|RuntimeOnly)$""")
    private val DECLARED_BUCKET_NAMES =
        setOf("annotationProcessor", "lintChecks", "lintPublish", "detektPlugins", "coreLibraryDesugaring")

    /** Production classpaths: every main and variant compile/runtime classpath, but no test classpath. */
    fun isCheckedClasspath(name: String): Boolean =
        (name == "compileClasspath" || name == "runtimeClasspath" || name.endsWith("CompileClasspath") ||
            name.endsWith("RuntimeClasspath")) &&
            !name.contains("test", ignoreCase = true)

    fun isAndroidGroup(group: String): Boolean = ANDROID_GROUPS.any { group == it || group.startsWith("$it.") }

    private fun isDeclaredBucket(name: String): Boolean =
        DECLARED_BUCKET_SUFFIX.containsMatchIn(name) ||
            name in DECLARED_BUCKET_NAMES ||
            name.startsWith("ksp") && !name.endsWith("Classpath") ||
            name.startsWith("kapt") && !name.endsWith("Classpath")

    private fun isFeature(path: String): Boolean = path.startsWith(":feature:")

    /** Checks on what each module declares, across all of its configurations. */
    fun declaredViolations(
        project: Project,
        androidProjects: Set<String>,
    ): List<String> {
        val path = project.path
        val found = mutableListOf<String>()
        val isPure = path in PURE_MODULES

        if (isPure && path in androidProjects) {
            found += "$path is a pure Kotlin module but applies an Android plugin. Use aegis.jvm.engine."
        }
        if (isPure && !project.pluginManager.hasPlugin("aegis.jvm.engine")) {
            found += "$path is a pure Kotlin module and must apply aegis.jvm.engine."
        }

        project.configurations.forEach { configuration ->
            val isTestConfiguration = configuration.name.contains("test", ignoreCase = true)
            configuration.dependencies.forEach { dependency ->
                when (dependency) {
                    is ProjectDependency -> {
                        val to = dependency.path
                        if (isFeature(path) && isFeature(to) && path != to) {
                            found += "$path → $to: feature modules never depend on each other."
                        }
                        if (isPure && to in androidProjects) {
                            found += "$path → $to: a pure module can't depend on an Android module."
                        }
                    }

                    is FileCollectionDependency -> {
                        if (isDeclaredBucket(configuration.name)) {
                            found += "$path (${configuration.name}): file dependencies bypass the version " +
                                "catalog and dependency verification. Use a catalog alias."
                        }
                    }

                    is ExternalModuleDependency -> {
                        val group = dependency.group.orEmpty()
                        if (isPure && !isTestConfiguration && isAndroidGroup(group)) {
                            found += "$path → $group:${dependency.name}: a pure module can't use Android libraries."
                        }
                    }
                }
            }
        }
        return found.distinct()
    }

    /** Checks on one resolved production classpath of [projectPath]. */
    fun resolvedViolations(
        projectPath: String,
        classpath: String,
        graph: DependencyGraph,
        androidProjects: Set<String>,
    ): List<String> {
        val found = mutableListOf<String>()
        val where = "$projectPath ($classpath)"

        if (projectPath in PURE_MODULES) {
            graph.walk { node ->
                when {
                    node is GraphNode.ModuleNode && isAndroidGroup(node.group) -> {
                        found += "$where reaches ${node.group}:${node.module}: a pure module can't use Android libraries."
                    }

                    node is GraphNode.ProjectNode && node.path in androidProjects -> {
                        found += "$where reaches ${node.path}: a pure module can't depend on an Android module."
                    }
                }
            }
        }

        if (isFeature(projectPath)) {
            graph.walk { node ->
                if (node is GraphNode.ProjectNode && isFeature(node.path) && node.path != projectPath) {
                    found += "$where reaches ${node.path}: feature modules never depend on each other."
                }
            }
        }

        // On a compile classpath, a restricted group shows up only if some module exposes it as
        // `api`, and that leak is itself a violation. On a runtime classpath, reaching the group
        // through an allowed module is fine.
        val isCompile = classpath == "compileClasspath" || classpath.endsWith("CompileClasspath")
        RESTRICTED_GROUPS.forEach { (group, allowed) ->
            if (projectPath in allowed) return@forEach
            val stopAt: (GraphNode) -> Boolean = { !isCompile && it is GraphNode.ProjectNode && it.path in allowed }
            graph.walk(stopAt) { node ->
                if (node is GraphNode.ModuleNode && node.group == group) {
                    found += "$where reaches $group:${node.module}: only ${allowed.joinToString()} may use $group."
                }
            }
        }
        return found.distinct()
    }
}

/** Root task: declared-dependency rules. It depends on every module's [CheckResolvedGraphTask]. */
abstract class CheckModuleGraphTask : DefaultTask() {
    @get:Input
    abstract val declaredViolations: ListProperty<String>

    @TaskAction
    fun check() {
        val found = declaredViolations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "Module graph violations (docs/01-architecture.md, Module layout):\n" + found.joinToString("\n") { "  - $it" },
            )
        }
        logger.lifecycle("Module graph OK.")
    }
}

/**
 * Per-module task: resolved-classpath rules. It lives in each module because a project may only
 * resolve its own configurations.
 */
abstract class CheckResolvedGraphTask : DefaultTask() {
    @get:Input
    abstract val projectPath: Property<String>

    /** Keyed by configuration name. */
    @get:Input
    abstract val classpaths: MapProperty<String, ResolvedComponentResult>

    @get:Input
    abstract val androidProjects: SetProperty<String>

    @TaskAction
    fun check() {
        val path = projectPath.get()
        val android = androidProjects.get()
        if (classpaths.get().isEmpty() && path in android) {
            throw GradleException("$path has no production classpath to check. Update ModuleGraphRules.isCheckedClasspath.")
        }
        val found =
            classpaths.get().flatMap { (classpath, root) ->
                ModuleGraphRules.resolvedViolations(path, classpath, DependencyGraph.from(root), android)
            }
        if (found.isNotEmpty()) {
            throw GradleException(
                "Module graph violations (docs/01-architecture.md, Module layout):\n" + found.distinct().joinToString("\n") { "  - $it" },
            )
        }
    }
}
