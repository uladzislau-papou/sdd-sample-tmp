package com.example.contractmanagement.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Executable form of `architecture.definition.md`'s dependency rules.
 *
 * These exist because the rules were previously enforced only when a human or an agent
 * remembered to check them (ADR-0007). Every rule below carries its `because(...)` text so
 * a failure names the document it violates, not just the class.
 *
 * SDD: see `documentation/adr/0007-archunit-boundary-enforcement.adr.md`.
 */
class DependencyRulesTest {
    companion object {
        private lateinit var production: JavaClasses

        @BeforeAll
        @JvmStatic
        fun importProductionClasses() {
            production = ArchitectureRoot.productionClasses()
        }
    }

    @Test
    @DisplayName("Rule 1: core.domain depends on nothing else in the project except shared.domain")
    fun rule1_coreDomainDependsOnNothingElse() {
        noClasses()
            .that().resideInAPackage("..core.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..core.inport..",
                "..core.outport..",
                "..shared.outport..",
                "..shared.outbound..",
                "..inbound..",
                "..outbound..",
                "..bootstrap..",
            ).because(
                "architecture.definition.md 4.1: core.domain MUST NOT depend on any other " +
                    "project package. Only shared.domain is permitted (9). shared.outport is " +
                    "included deliberately: a domain class injecting ClockPort would satisfy " +
                    "8 in letter while evading it in substance.",
            ).whileTheContractSliceIsIncomplete()
            .check(production)
    }

    @Test
    @DisplayName("Rule 2: core and the shared kernel's contracts are framework-free")
    fun rule2_contractsAreFrameworkFree() {
        noClasses()
            .that().resideInAnyPackage(
                "..core.inport..",
                "..core.outport..",
                "..core.domain..",
                "..shared.domain..",
                "..shared.outport..",
            ).should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta..",
                "com.fasterxml..",
                "tools.jackson..",
                "graphql..",
            ).because(
                "architecture.definition.md 4.2, 4.3 and 9: the framework-free core is what " +
                    "lets the same use case be driven by REST, GraphQL or a listener without " +
                    "the core knowing which.",
            ).check(production)
    }

    @Test
    @DisplayName("Rule 3b: inbound.graphql obeys the same rule as inbound.rest")
    fun rule3b_graphqlDependsOnInportOnly() {
        noClasses()
            .that().resideInAPackage("..inbound.graphql..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..core.outport..", "..inbound.driver..", "..outbound..")
            .because(
                "coding-style.definition.md 3.3: every delivery adapter obeys the same rule. " +
                    "A second transport arriving with weaker constraints is how the core stops " +
                    "being transport-agnostic.",
            ).whileTheContractSliceIsIncomplete()
            .check(production)
    }

    @Test
    @DisplayName("Rule 3c: no delivery adapter depends on another transport")
    fun rule3c_transportsDoNotDependOnEachOther() {
        noClasses()
            .that().resideInAPackage("..inbound.graphql..")
            .should().dependOnClassesThat().resideInAPackage("..inbound.rest..")
            .because(
                "the two adapters exist to prove the core is reachable from either. Sharing a " +
                    "DTO between them would couple both to one transport's representation.",
            ).whileTheContractSliceIsIncomplete()
            .check(production)
    }

    @Test
    @DisplayName("Rule 4: inbound.driver depends only on the core")
    fun rule4_driverDependsOnCoreOnly() {
        noClasses()
            .that().resideInAPackage("..inbound.driver..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..inbound.rest..",
                "..inbound.graphql..",
                "..inbound.listener..",
                "..outbound..",
            ).because(
                "architecture.definition.md 4.4: a driver MAY depend on core.domain, " +
                    "core.inport and core.outport, and on nothing else.",
            ).whileTheContractSliceIsIncomplete()
            .check(production)
    }

    @Test
    @DisplayName("Rule 5: only bootstrap references any concrete outbound adapter")
    fun rule5_outboundIsReferencedOnlyByBootstrap() {
        classes()
            .that().resideInAPackage("..outbound..")
            .should().onlyHaveDependentClassesThat()
            .resideInAnyPackage("..outbound..", "..bootstrap..")
            .because(
                "architecture.definition.md 6 rule 5: outbound implementations are reached " +
                    "through their port, and wired in one place.",
            ).check(production)
    }

    @Test
    @DisplayName("Rule 6: no persistence type crosses into the core")
    fun rule6_noPersistenceTypeInTheCore() {
        noClasses()
            .that().resideInAnyPackage("..core..", "..shared.domain..", "..shared.outport..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "org.springframework.data..")
            .because(
                "this is the one persistence rule the template fixes: the domain holds no " +
                    "persistence annotations. The ORM itself is a project choice " +
                    "(technical.spec.md), which is why this rule names the leak rather than " +
                    "the technology — it catches the next ORM too.",
            ).check(production)
    }

    @Test
    @DisplayName("Rule 7: bootstrap contains only configuration")
    fun rule7_bootstrapContainsOnlyConfiguration() {
        classes()
            .that().resideInAPackage("..bootstrap..")
            .should().haveSimpleNameEndingWith("Config")
            .because(
                "architecture.definition.md 4.9: bootstrap is the composition root and holds " +
                    "wiring only. The entry point itself lives in the root package, so that " +
                    "Spring's component scan and the architecture tests both derive the " +
                    "package root from where the code actually is.",
            ).check(production)
    }

    @Test
    @DisplayName("Layered architecture: the core is not reached from outside inward")
    fun layering_coreDoesNotDependOnAdapters() {
        noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..inbound..", "..outbound..", "..bootstrap..")
            .because(
                "architecture.definition.md 2: the core is stable and the adapters are " +
                    "replaceable, which only holds while the arrows point inward.",
            ).whileTheContractSliceIsIncomplete()
            .check(production)
    }
}
