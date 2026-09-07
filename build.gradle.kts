// Root build file. It builds nothing — `app` is the only module. It exists for one task.

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
                root.resolve("app/src/main/kotlin").walkTopDown().firstOrNull { it.name == "ServiceApplication.kt" }
                    ?: error(
                        "Could not find ServiceApplication.kt under app/src/main/kotlin. This task " +
                            "locates the package root from the entry point; if the entry point moved out " +
                            "of the root package, the component scan is broken too (architecture.definition.md 4.9).",
                    )
            return entryPoint.parentFile
                .relativeTo(root.resolve("app/src/main/kotlin"))
                .path
                .replace(File.separatorChar, '.')
        }

        fun movePackages(
            root: File,
            oldPackage: String,
            newPackage: String,
        ) {
            listOf("app/src/main/kotlin", "app/src/test/kotlin").forEach { sourceRoot ->
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

            val appBuild = root.resolve("app/build.gradle.kts")
            appBuild.writeText(
                appBuild.readText().replace(Regex("""^group = ".*"$""", RegexOption.MULTILINE), "group = \"$group\""),
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
                "app/src/main/resources/application.yml",
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
