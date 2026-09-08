package com.example.contractmanagement.architecture

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Reads the bounded-context registry out of `architecture.definition.md` § 11.
 *
 * The registry used to be duplicated: prose in the document and a string literal in the
 * test. With two contexts that never changed it looked harmless, but adding a context
 * meant editing both, and the test would have gone on passing against the stale list —
 * green for a registry that no longer described the code.
 *
 * So the document is the single source and this parses it. `CLAUDE.md` already names two
 * lists that must live in exactly one place; this is the third, and it is now enforced
 * rather than asserted.
 *
 * The shape it depends on is written beside the table in § 11 under "Format contract".
 */
object ContextRegistry {
    private const val SECTION_HEADING = "## 11. Registered Bounded Contexts"
    private const val BOUNDED_CONTEXT = "Bounded Context"

    data class Row(
        val packageName: String,
        val kind: String,
    )

    val rows: List<Row> by lazy { parse(readDefinition()) }

    /** Packages registered as bounded contexts. */
    val boundedContexts: List<String> get() = rows.filter { it.kind == BOUNDED_CONTEXT }.map { it.packageName }

    /** Every registered top-level package, contexts and non-contexts alike. */
    val registeredPackages: List<String> get() = rows.map { it.packageName }

    private fun readDefinition(): String {
        val relative = Paths.get("documentation", "architecture.definition.md")
        var dir: Path? = Paths.get("").toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve(relative)
            if (Files.exists(candidate)) {
                return Files.readString(candidate)
            }
            dir = dir.parent
        }
        error(
            "Could not find $relative by walking up from ${Paths.get("").toAbsolutePath()}. " +
                "The registry lives in the document, so the test cannot run without it.",
        )
    }

    private fun parse(markdown: String): List<Row> {
        val sectionStart = markdown.indexOf(SECTION_HEADING)
        require(sectionStart >= 0) {
            "'$SECTION_HEADING' not found in architecture.definition.md. " +
                "The heading is part of the format contract in § 11 — renaming it disarms this gate."
        }

        val lines = markdown.substring(sectionStart).lineSequence()
        val rows =
            lines
                .dropWhile { !it.startsWith("| Package ") } // table header
                .drop(2) // header and separator
                .takeWhile { it.startsWith("|") }
                .mapNotNull { row ->
                    val cells = row.trim().trim('|').split("|").map(String::trim)
                    if (cells.size < 2) return@mapNotNull null
                    val name = cells[0].trim('`')
                    if (name.isEmpty()) null else Row(name, cells[1])
                }.toList()

        require(rows.isNotEmpty()) {
            "The § 11 registry table parsed to zero rows. Either the table was restyled or " +
                "the format contract beside it was not followed — see § 11."
        }
        return rows
    }
}
