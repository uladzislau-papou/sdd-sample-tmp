package com.dominikgaller.alpinebooking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Mechanical enforcement of the seven dependency rules in
 * {@code documentation/architecture.definition.md} section 6.
 *
 * <p>Each test names the rule it enforces. The definition stays authoritative: if a test
 * and the definition ever disagree, the definition wins and the test is wrong
 * (ADR 0007).
 *
 * <p>SDD: See {@code documentation/adr/0007-archunit-boundary-enforcement.adr.md}.
 */
class DependencyRulesTest {

    private static final String ROOT = "com.dominikgaller.alpinebooking";

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                // jOOQ generates this package; it is not hand-written architecture.
                .withImportOption(location -> !location.contains("/jooq/"))
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("Rule 1: core.domain depends on nothing else in the project except shared.domain")
    void rule1_coreDomainDependsOnNothingElse() {
        noClasses()
                .that().resideInAPackage("..core.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..core.inport..",
                        "..core.outport..",
                        "..inbound..",
                        "..outbound..",
                        "..bootstrap..",
                        "..shared.outbound..")
                .because("architecture.definition.md 4.1: core.domain MUST NOT depend on any "
                        + "other project package. Only shared.domain is permitted (9).")
                .check(production);
    }

    @Test
    @DisplayName("Rule 2: core.inport and core.outport are framework-free")
    void rule2_inportAndOutportAreFrameworkFree() {
        noClasses()
                .that().resideInAnyPackage("..core.inport..", "..core.outport..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "com.fasterxml..",
                        "org.jooq..",
                        "io.swagger..")
                .because("architecture.definition.md 4.2 and 4.3: inport and outport are "
                        + "framework-free contracts.")
                .check(production);
    }

    @Test
    @DisplayName("Rule 3: inbound.rest depends only on core.inport, not on core.outport")
    void rule3_inboundRestDoesNotDependOnOutport() {
        noClasses()
                .that().resideInAPackage("..inbound.rest..")
                .should().dependOnClassesThat().resideInAPackage("..core.outport..")
                .because("architecture.definition.md 6 rule 3 and 4.5: controllers MUST "
                        + "depend only on core.inport interfaces.")
                .check(production);
    }

    @Test
    @DisplayName("Rule 3b: inbound.rest does not reach persistence or integration adapters")
    void rule3b_inboundRestDoesNotDependOnOutboundAdapters() {
        noClasses()
                .that().resideInAPackage("..inbound.rest..")
                .should().dependOnClassesThat().resideInAPackage("..outbound..")
                .because("architecture.definition.md 4.5: controllers MUST NOT access "
                        + "repositories or outbound implementations.")
                .check(production);
    }

    @Test
    @DisplayName("Rule 4: inbound.driver never names a concrete outbound adapter")
    void rule4_driversDoNotDependOnOutboundImplementations() {
        noClasses()
                .that().resideInAPackage("..inbound.driver..")
                .should().dependOnClassesThat().resideInAPackage("..outbound..")
                .because("architecture.definition.md 4.4: drivers depend on core.domain, "
                        + "core.inport and core.outport only - never on a concrete adapter.")
                .check(production);
    }

    @Test
    @DisplayName("Rule 5: only bootstrap references concrete outbound adapters")
    void rule5_onlyBootstrapWiresAdapters() {
        classes()
                .that().resideInAPackage("..outbound.integration..")
                .and().haveSimpleNameNotEndingWith("Mapper")
                .should().onlyHaveDependentClassesThat()
                .resideInAnyPackage("..outbound..", "..bootstrap..")
                .because("architecture.definition.md 6 rule 5: outbound implementations must "
                        + "not be referenced as concrete types outside bootstrap.")
                .check(production);
    }

    @Test
    @DisplayName("Rule 6: no persistence type crosses into the core")
    void rule6_noPersistenceTypesInTheCore() {
        noClasses()
                .that().resideInAnyPackage("..core..", "..shared.domain..", "..shared.outport..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(ROOT + ".jooq..", "org.jooq..", "jakarta.persistence..")
                .because("architecture.definition.md 6 rule 6: no jOOQ or JPA type may "
                        + "appear in a port or in the domain.")
                .check(production);
    }

    @Test
    @DisplayName("Rule 7: bootstrap contains only configuration")
    void rule7_bootstrapContainsOnlyWiring() {
        classes()
                .that().resideInAPackage("..bootstrap..")
                .should().haveSimpleNameEndingWith("Config")
                .orShould().haveSimpleNameEndingWith("Application")
                .because("architecture.definition.md 4.9: bootstrap is the composition root "
                        + "and contains wiring only - no business or domain logic.")
                .check(production);
    }

    @Test
    @DisplayName("Layered architecture: core is not reached from outside inward")
    void layering_coreIsNotDependentOnOuterLayers() {
        noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..inbound..", "..outbound..", "..bootstrap..")
                .because("architecture.definition.md 2: the core is stable and "
                        + "technology-agnostic; dependencies point inward.")
                .check(production);
    }
}
