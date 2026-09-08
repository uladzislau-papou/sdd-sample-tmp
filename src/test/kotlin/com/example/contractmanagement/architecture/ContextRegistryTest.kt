package com.example.contractmanagement.architecture

import com.example.contractmanagement.architecture.ArchitectureRoot.ROOT
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Enforces `architecture.definition.md` § 11 — the bounded-context registry — against the
 * code, with the registry read from the document rather than restated here.
 *
 * SDD: see `documentation/architecture.definition.md` § 11 and
 * `documentation/adr/0007-archunit-boundary-enforcement.adr.md`.
 */
class ContextRegistryTest {
    companion object {
        private lateinit var production: JavaClasses

        @BeforeAll
        @JvmStatic
        fun importProductionClasses() {
            production = ArchitectureRoot.productionClasses()
        }
    }

    @Test
    @DisplayName("§ 11: top-level packages are exactly the registered ones")
    fun topLevelPackagesMatchTheRegistry() {
        val onDisk =
            production
                .asSequence()
                .map { it.packageName }
                .filter { it.startsWith("$ROOT.") }
                .map { it.removePrefix("$ROOT.").substringBefore('.') }
                .distinct()
                .sorted()
                .toList()

        assertThat(onDisk)
            .describedAs(
                "Top-level packages on disk must match the § 11 registry exactly. An " +
                    "unregistered package is drift regardless of how reasonable it looks; a " +
                    "registered package that does not exist is a stale row. Registering a " +
                    "context starts with the document (§ 11), not with a mkdir.",
            ).containsExactlyInAnyOrderElementsOf(ContextRegistry.registeredPackages)
    }

    @Test
    @DisplayName("§ 11: the registry declares at least one bounded context and the shared kernel")
    fun theRegistryIsParsedAndPlausible() {
        assertThat(ContextRegistry.boundedContexts)
            .describedAs("§ 11 must declare at least one bounded context")
            .isNotEmpty()
        assertThat(ContextRegistry.registeredPackages)
            .describedAs("the shared kernel and the composition root are registered packages too")
            .contains("shared", "bootstrap")
    }

    @Test
    @DisplayName("§ 11 rule 3: a context depends only on another context's published inport")
    fun contextsDoNotImportEachOthersInternals() {
        val contexts = ContextRegistry.boundedContexts
        contexts.forEach { context ->
            val others = contexts.filter { it != context }
            others.forEach { other ->
                noClasses()
                    .that().resideInAPackage("$ROOT.$context..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                        "$ROOT.$other.core.domain..",
                        "$ROOT.$other.core.outport..",
                        "$ROOT.$other.inbound..",
                        "$ROOT.$other.outbound..",
                    ).because(
                        "architecture.definition.md § 11 rule 3: a context's core.inport is its " +
                            "published API and everything else is closed. This is the rule that " +
                            "keeps '$context' and '$other' separately deployable in principle, " +
                            "and it is the cheapest one to break by accident.",
                    ).check(production)
            }
        }
    }

    @Test
    @DisplayName("§ 11 rule 4: shared depends on no bounded context")
    fun sharedDependsOnNoBoundedContext() {
        val contextPackages = ContextRegistry.boundedContexts.map { "$ROOT.$it.." }.toTypedArray()

        noClasses()
            .that().resideInAPackage("$ROOT.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(*contextPackages)
            .because(
                "architecture.definition.md § 9 and § 11 rule 4: adding a context-specific " +
                    "type to shared is drift even though shared is registered. This rule is " +
                    "also why a shared value object cannot throw a context's domain exception " +
                    "— see coding-style.definition.md § 6.3.",
            ).check(production)
    }

    @Test
    @DisplayName("§ 11 rule 5: every context follows the same internal ontology")
    fun everyContextFollowsTheSameOntology() {
        val permitted = setOf("core", "inbound", "outbound")

        ContextRegistry.boundedContexts.forEach { context ->
            val prefix = "$ROOT.$context."
            val layers =
                production
                    .asSequence()
                    .map { it.packageName }
                    .filter { it.startsWith(prefix) }
                    .map { it.removePrefix(prefix).substringBefore('.') }
                    .distinct()
                    .toList()

            assertThat(layers)
                .describedAs(
                    "context '%s' must use the ontology from § 3: core / inbound / outbound. " +
                        "A context inventing its own internal layout is drift — it makes every " +
                        "dependency rule in § 6 unenforceable for that context.",
                    context,
                ).isSubsetOf(permitted)
        }
    }
}
