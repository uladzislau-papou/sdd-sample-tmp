// The single build file. The project used to be an `app` subproject wrapping `app/src`; the
// module was collapsed into the root because one module behind a subproject directory is
// a level of nesting that buys nothing and shows up in every path in the documentation.
// Sources now live at `src/`, and this file carries both the module configuration and the
// `initService` task.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management.plugin)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "com.example"

val javaVersion = libs.versions.java.get().toInt()

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.graphql)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.graphql.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.boot.data.jpa.test)
    testImplementation(libs.spring.boot.jdbc.test)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
}

// The toolchain is the single authoritative Java version declaration (ADR 0006).
// The vendor is deliberately not pinned: the baseline is an LTS release, so the
// early-access ambiguity that justified pinning a vendor for Java 25 no longer
// applies, and pinning would force a second JDK download on machines that already
// have a matching one.
kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        allWarningsAsErrors.set(true)
    }
}

// `test.definition.md` already separates fast tests from adapter integration tests by
// name. The build now separates them too, because one Gradle task that needs Docker gives
// a developer without Docker no fast feedback at all — and a gate that cannot be run
// locally is a gate that gets discovered in CI.
//
// `test`            — domain, use case and slice tests. No Docker, seconds.
// `integrationTest` — every `*IT`. Starts PostgreSQL through Testcontainers.
// `check`           — depends on both, so nothing is quietly skipped in CI.
tasks.named<Test>("test") {
    useJUnitPlatform()
    filter { excludeTestsMatching("*IT") }
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs adapter integration tests (*IT) against real infrastructure. Requires Docker."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*IT") }
    shouldRunAfter(tasks.named("test"))
}

// Formatting gate (technical.spec.md, Build & Quality Gates).
// Unlike the Java baseline this replaces, ktlint *does* apply a format: Kotlin has one
// community style and no hand-written house style to preserve, so there is nothing to
// bury. `spotlessApply` is the fix; `spotlessCheck` is the gate.
spotless {
    val excluded = listOf("**/build/**", "**/.gradle/**")

    kotlin {
        target("src/**/*.kt")
        targetExclude(excluded)
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.kts")
        targetExclude(excluded)
        ktlint(libs.versions.ktlint.get())
    }
}

// Static analysis gate. Detekt catches the defect classes ArchUnit cannot see —
// complexity, swallowed exceptions, platform-type leaks — and ArchUnit catches the
// ones detekt cannot: layering and dependency direction. Neither replaces the other.
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("config/detekt/detekt.yml"))
}

// Both gates are wired explicitly rather than relying on either plugin's defaults:
// `./gradlew build` must fail on a style or static-analysis violation, and a plugin
// that silently stops contributing to `check` is a gate that silently disappears.
tasks.named("check") {
    dependsOn(integrationTest)
    dependsOn(tasks.named("spotlessCheck"))
    dependsOn(tasks.withType<dev.detekt.gradle.Detekt>())
}

/**
 * Gives a copy of this template a new identity.
 *
 * ```
 * ./gradlew initService -PserviceName=billing -PserviceGroup=com.acme
 * ```
 *
 * Why a task and not a plain Gradle property. A property can set `group` and
 * `rootProject.name`; it cannot move source directories or rewrite `package` declarations,
 * and those are where the identity actually lives — in ~100 files. The alternative was
 * placeholder tokens like `__PACKAGE__` throughout the tree, which was rejected because a
 * template full of placeholders does not compile, so it can never be built, tested, or
 * shown to work. This template stays a green, running service and renames itself on
 * demand.
 *
 * Idempotent in the useful direction: running it twice with the same arguments is a no-op,
 * because the second run finds nothing left under the old package root.
 */
val initService by tasks.registering {
    group = "template"
    description = "Renames this template copy into a new service. Args: -PserviceName, -PserviceGroup"

    // Captured at configuration time so the task body touches no Project state, which is
    // what keeps the configuration cache usable.
    val serviceName = providers.gradleProperty("serviceName")
    val serviceGroup = providers.gradleProperty("serviceGroup")
    val root = layout.projectDirectory.asFile

    doLast {
        /**
         * Finds the current package root by locating the entry point, rather than assuming it.
         *
         * The same reason `ArchitectureRoot` derives it in the tests: a hardcoded string here would
         * be a fourth copy of the package root, and this task's whole job is to change it.
         */
        fun currentPackageRoot(root: File): String {
            val entryPoint =
                root.resolve("src/main/kotlin").walkTopDown().firstOrNull { it.name == "ServiceApplication.kt" }
                    ?: error(
                        "Could not find ServiceApplication.kt under src/main/kotlin. This task " +
                            "locates the package root from the entry point; if the entry point moved out " +
                            "of the root package, the component scan is broken too (architecture.definition.md 4.9).",
                    )
            return entryPoint.parentFile
                .relativeTo(root.resolve("src/main/kotlin"))
                .path
                .replace(File.separatorChar, '.')
        }

        fun movePackages(
            root: File,
            oldPackage: String,
            newPackage: String,
        ) {
            listOf("src/main/kotlin", "src/test/kotlin").forEach { sourceRoot ->
                val base = root.resolve(sourceRoot)
                val from = base.resolve(oldPackage.replace('.', '/'))
                if (!from.isDirectory) return@forEach

                val to = base.resolve(newPackage.replace('.', '/'))
                to.parentFile.mkdirs()
                check(from.renameTo(to)) { "Could not move $from to $to" }

                // Leave no empty com/example/ behind.
                var stale = from.parentFile
                while (stale != base && stale.isDirectory && stale.list()?.isEmpty() == true) {
                    val parent = stale.parentFile
                    stale.delete()
                    stale = parent
                }
            }
        }

        fun rewriteSources(
            root: File,
            oldPackage: String,
            newPackage: String,
        ) {
            val extensions = setOf("kt", "kts", "graphqls", "yml", "yaml", "sql")
            var touched = 0
            root.walkTopDown()
                .onEnter { it.name !in setOf("build", ".git", ".gradle", ".idea", ".kotlin") }
                .filter { it.isFile && it.extension in extensions }
                .forEach { file ->
                    val before = file.readText()
                    if (!before.contains(oldPackage)) return@forEach
                    file.writeText(before.replace(oldPackage, newPackage))
                    touched++
                }
            println("  rewrote $touched source and resource files")
        }

        fun rewriteBuildFiles(
            root: File,
            name: String,
            group: String,
        ) {
            val settings = root.resolve("settings.gradle.kts")
            settings.writeText(
                settings.readText().replace(
                    Regex("""rootProject\.name = ".*""""),
                    "rootProject.name = \"$name\"",
                ),
            )

            val build = root.resolve("build.gradle.kts")
            build.writeText(
                build.readText().replace(Regex("""^group = ".*"$""", RegexOption.MULTILINE), "group = \"$group\""),
            )
            println("  set rootProject.name=$name and group=$group")
        }

        /**
         * The database name, the compose container name and `spring.application.name` all carry the
         * old service name. Left alone, a second service started from this template would fight the
         * first one for the same container name and the same database.
         */
        fun rewriteRuntimeConfig(
            root: File,
            name: String,
        ) {
            val databaseName = name.replace("-", "_")
            val replacements =
                listOf(
                    "service_template" to databaseName,
                    "service-template" to name,
                )
            listOf(
                "src/main/resources/application.yml",
                "docker-compose/docker-compose.yaml",
            ).forEach { path ->
                val file = root.resolve(path)
                if (!file.isFile) return@forEach
                var text = file.readText()
                replacements.forEach { (from, to) -> text = text.replace(from, to) }
                file.writeText(text)
            }
            println("  pointed the datasource and compose stack at '$databaseName'")
        }

        /**
         * Replaces the example's vision document with the blank.
         *
         * Not cosmetic. `project.definition.md` is the highest-ranked document in `CLAUDE.md`'s
         * authority order, so leaving the tour-booking one in place means every agent run opens by
         * reading about somebody else's domain.
         */
        fun installProjectDefinitionTemplate(root: File) {
            val template = root.resolve("documentation/project.definition.template.md")
            val target = root.resolve("documentation/project.definition.md")
            if (!template.isFile) {
                println("  project.definition.template.md is missing — leaving project.definition.md alone")
                return
            }
            target.writeText(template.readText())
            println("  installed the blank project.definition.md (fill it in first)")
        }

        val name =
            serviceName.orNull ?: error(
                "Missing -PserviceName. Example: ./gradlew initService -PserviceName=billing -PserviceGroup=com.acme",
            )
        val group =
            serviceGroup.orNull ?: error(
                "Missing -PserviceGroup. Example: ./gradlew initService -PserviceName=billing -PserviceGroup=com.acme",
            )

        require(name.matches(Regex("[a-z][a-z0-9-]*"))) {
            "serviceName must be lowercase kebab-case, e.g. 'risk-management'. Got: '$name'"
        }
        require(group.matches(Regex("[a-z][a-z0-9.]*[a-z0-9]"))) {
            "serviceGroup must be a lowercase package prefix, e.g. 'com.acme'. Got: '$group'"
        }

        // A package segment cannot contain a hyphen, but a project name reads better with
        // one. Keep both forms rather than forcing one on the other.
        val packageLeaf = name.replace("-", "")
        val newPackage = "$group.$packageLeaf"
        val oldPackage = currentPackageRoot(root)

        if (oldPackage == newPackage) {
            logger.lifecycle("Already '$newPackage' — nothing to do.")
            return@doLast
        }

        logger.lifecycle("Renaming $oldPackage -> $newPackage")

        movePackages(root, oldPackage, newPackage)
        rewriteSources(root, oldPackage, newPackage)
        rewriteBuildFiles(root, name, group)
        rewriteRuntimeConfig(root, name)
        installProjectDefinitionTemplate(root)

        logger.lifecycle(
            """
            |
            |Done. Next:
            |  1. Fill in documentation/project.definition.md — it is document number one in
            |     the authority order, and its Non-Goals section is the part that pays off.
            |  2. Decide what happens to the booking/ and guide/ example contexts. Deleting one
            |     means deleting its row from architecture.definition.md section 11 — that table
            |     is parsed by ContextRegistryTest, so the two cannot disagree.
            |  3. docker compose -f docker-compose/docker-compose.yaml up -d && ./gradlew build
            |
            """.trimMargin(),
        )
    }
}
