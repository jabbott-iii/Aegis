import io.github.jabbottiii.aegis.buildlogic.CheckModuleGraphTask
import io.github.jabbottiii.aegis.buildlogic.CheckResolvedGraphTask
import io.github.jabbottiii.aegis.buildlogic.CheckSourceRulesTask
import io.github.jabbottiii.aegis.buildlogic.ModuleGraphRules
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/** Applied to the root project only. Registers `checkModuleGraph` and `checkSourceRules`. */
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) { "aegis.root must be applied to the root project" }
        val declared = target.objects.listProperty(String::class.java)
        val checkModuleGraph =
            target.tasks.register<CheckModuleGraphTask>("checkModuleGraph") {
                group = "verification"
                description = "Checks the module dependency rules in ARCHITECTURE §3."
                declaredViolations.set(declared)
            }
        target.gradle.projectsEvaluated {
            val androidProjects =
                target.subprojects
                    .filter { it.pluginManager.hasPlugin("com.android.base") }
                    .map { it.path }
                    .toSet()
            declared.set(target.subprojects.flatMap { ModuleGraphRules.declaredViolations(it, androidProjects) })
            val perModule =
                target.subprojects.map { project ->
                    project.tasks.register<CheckResolvedGraphTask>("checkResolvedGraph") {
                        description = "Checks this module's resolved classpaths against ARCHITECTURE §3."
                        projectPath.set(project.path)
                        this.androidProjects.set(androidProjects)
                        project.configurations
                            .filter { ModuleGraphRules.isCheckedClasspath(it.name) && it.isCanBeResolved }
                            .forEach { classpaths.put(it.name, it.incoming.resolutionResult.rootComponent) }
                    }
                }
            checkModuleGraph.configure { dependsOn(perModule) }
        }
        target.tasks.register<CheckSourceRulesTask>("checkSourceRules") {
            group = "verification"
            description = "Bans direct logging, hard-coded dispatchers, GlobalScope, runBlocking, and engine `!!`."
            rootPath.set(target.rootDir.absolutePath)
            // Each module's own src/ tree, so build outputs never show up and a package directory
            // that happens to be named "build" is still scanned. Test source sets (test, testDebug,
            // androidTest, screenshotTest, testFixtures, ...) and the testing:* helpers may print.
            target.subprojects
                .filterNot { it.path.startsWith(":testing") }
                .forEach { project ->
                    sources.from(
                        target.fileTree(project.projectDir.resolve("src")) {
                            include("**/*.kt", "**/*.java")
                            exclude("test*/**", "androidTest*/**", "screenshotTest*/**")
                        },
                    )
                }
        }
    }
}
