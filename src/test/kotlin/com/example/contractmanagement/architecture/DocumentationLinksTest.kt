package com.example.contractmanagement.architecture

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
 * Every ADR referenced from the documentation exists.
 *
 * The same reasoning as [SpecCitationsTest], applied to a different rot class. ADR
 * references are the load-bearing links in this project: `architecture.definition.md` § 11
 * cites one per registered context, `sdd.playbook.md` points at one as the precedent for a
 * rule, and the code's KDoc cites them for the decisions it implements. A renamed or
 * renumbered ADR turns all of those into dead ends silently — markdown links do not fail.
 *
 * This test earned its place immediately: rebuilding the ADR set left exactly one dangling
 * reference, in a document that had been edited minutes earlier by the same hand that
 * renumbered the ADR.
 */
class DocumentationLinksTest {
    @Test
    @DisplayName("Every ADR reference in documentation/ points at a file that exists")
    fun everyAdrReferenceResolves() {
        val adrDirectory = repoRoot().resolve(Paths.get("documentation", "adr"))
        val existing =
            Files.list(adrDirectory).use { stream ->
                stream.map { it.name }.filter { it.endsWith(".adr.md") }.toList().toSet()
            }

        assertThat(existing)
            .describedAs("no ADR files were found — the scan is looking in the wrong place")
            .isNotEmpty()

        val pattern = Regex("adr/([0-9]{4}-[a-z0-9\\-]*\\.adr\\.md)")
        val dangling =
            markdownFiles(repoRoot().resolve("documentation")).flatMap { file ->
                pattern.findAll(file.readText())
                    .map { file.name to it.groupValues[1] }
                    .filterNot { it.second in existing }
            }

        assertThat(dangling)
            .describedAs(
                "A reference to an ADR that does not exist is a dead end that markdown will " +
                    "never complain about. Point it at the ADR that superseded it — an ADR's " +
                    "status may change, so a superseded file stays in place and stays a valid " +
                    "link target.",
            ).isEmpty()
    }

    private fun markdownFiles(root: Path): List<Path> =
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension == "md" }.toList()
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
