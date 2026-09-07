package com.example.service.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Every `SomeTest.some_method` citation in `documentation/` names a test that exists.
 *
 * The specs' Definition of Done is built on citations: `test.definition.md` § 9 requires an
 * item to name the test that satisfies it rather than claim "implemented". That makes the
 * DoD checkable — and makes a renamed test method quietly turn a ticked box into a claim
 * about nothing.
 *
 * This is not hypothetical. Porting the example from Java to Kotlin renamed nearly every
 * test method, and 66 of 69 citations across four specs went stale in one commit while
 * every box stayed ticked and every gate stayed green. The specs *looked* complete. This
 * test is what makes that class of drift loud, and it also turned up three genuine
 * coverage gaps the rename had hidden.
 *
 * Deliberately a test rather than a script: a script runs when somebody remembers, which
 * is the failure mode ADR-0007 exists to end.
 */
class SpecCitationsTest {
    @Test
    @DisplayName("Every test citation in documentation/ resolves to a test that exists")
    fun everyCitationResolves() {
        val declared = declaredTestMethods()
        assertThat(declared)
            .describedAs("no Kotlin test classes were found — the scan is looking in the wrong place")
            .isNotEmpty()

        val unresolved =
            citations().filterNot { (_, cls, method) -> method in declared.getOrDefault(cls, emptySet()) }

        assertThat(unresolved)
            .describedAs(
                "A Definition of Done item citing a test that does not exist is a ticked box " +
                    "claiming nothing. Either the test was renamed — update the citation — or " +
                    "the coverage it claimed never existed, in which case write the test. Do " +
                    "not delete the citation to make this pass.",
            ).isEmpty()
    }

    private data class Citation(
        val file: String,
        val testClass: String,
        val testMethod: String,
    )

    private fun citations(): List<Citation> {
        val pattern = Regex("`([A-Z][A-Za-z0-9]*(?:Test|IT))\\.([A-Za-z0-9_]+)`")
        return markdownFiles(repoRoot().resolve("documentation")).flatMap { file ->
            pattern.findAll(file.readText()).map {
                Citation(file.name, it.groupValues[1], it.groupValues[2])
            }
        }
    }

    private fun declaredTestMethods(): Map<String, Set<String>> {
        val pattern = Regex("fun ([A-Za-z0-9_]+)\\(")
        return kotlinFiles(repoRoot().resolve(Paths.get("src", "test", "kotlin")))
            .associate { file ->
                file.name.removeSuffix(".kt") to
                    pattern.findAll(file.readText()).map { it.groupValues[1] }.toSet()
            }
    }

    private fun markdownFiles(root: Path): List<Path> = filesUnder(root, "md")

    private fun kotlinFiles(root: Path): List<Path> = filesUnder(root, "kt")

    private fun filesUnder(
        root: Path,
        extension: String,
    ): List<Path> =
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension == extension }.toList()
        }

    private fun repoRoot(): Path {
        var dir: Path? = Paths.get("").toAbsolutePath()
        while (dir != null) {
            if (Files.exists(dir.resolve("documentation")) && Files.exists(dir.resolve("CLAUDE.md"))) {
                return dir
            }
            dir = dir.parent
        }
        error("Could not locate the repository root from ${Paths.get("").toAbsolutePath()}")
    }
}
