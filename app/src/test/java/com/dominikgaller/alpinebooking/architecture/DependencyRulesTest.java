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
                        "..shared.outport..",
                        "..shared.outbound..",
                        "..inbound..",
                        "..outbound..",
                        "..bootstrap..")
                .because("architecture.definition.md 4.1: core.domain MUST NOT depend on any "
                        + "other project package. Only shared.domain is permitted (9). "
                        + "shared.outport is included deliberately: a domain class injecting "
                        + "ClockPort would satisfy 8 in letter while evading it in substance, "
                        + "and domain_neverCallsNowDirectly cannot catch clockPort.now().")
                .check(production);
    }

    @Test
    @DisplayName("Rule 2: core and the shared kernel's contracts are framework-free")
    void rule2_contractsAreFrameworkFree() {
        noClasses()
                .that().resideInAnyPackage(
                        "..core.inport..",
                        "..core.outport..",
                        "..core.domain..",
                        "..shared.domain..",
                        "..shared.outport..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "com.fasterxml..",
                        "org.jooq..",
                        "io.swagger..")
                .because("architecture.definition.md 4.2, 4.3 and 9: the framework-free "
                        + "constraint applies to shared.domain and shared.outport exactly as "
                        + "it applies to a context's core. Only shared.outbound may use a "
                        + "framework.")
                .check(production);
    }

    @Test
    @DisplayName("Rule 3: inbound.rest depends on core.inport, its DTOs and domain exceptions only")
    void rule3_inboundRestDependsOnlyOnInportAndDomainExceptions() {
        noClasses()
                .that().resideInAPackage("..inbound.rest..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..core.outport..", "..inbound.driver..", "..outbound..")
                .because("architecture.definition.md 6 rule 3 and 4.5: controllers depend on "
                        + "core.inport interfaces - never on a driver, never on core.outport, "
                        + "never on an outbound implementation. Injecting a *Driver instead of "
                        + "its *UseCase is the canonical form of this violation. Domain "
                        + "exceptions are permitted, solely so *ExceptionHandler can map them.")
                .check(production);
    }

    @Test
    @DisplayName("Rule 4: inbound.driver depends only on the core")
    void rule4_driversDependOnlyOnTheCore() {
        noClasses()
                .that().resideInAPackage("..inbound.driver..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..inbound.rest..", "..inbound.listener..", "..outbound..")
                .because("architecture.definition.md 4.4: a driver MAY depend on core.domain, "
                        + "core.inport and core.outport, and MUST NOT depend on inbound.rest, "
                        + "outbound implementations or other adapters. A driver importing a "
                        + "REST DTO previously passed every rule in this suite.")
                .check(production);
    }

    @Test
    @DisplayName("Rule 5: only bootstrap references any concrete outbound adapter")
    void rule5_onlyBootstrapWiresAdapters() {
        classes()
                .that().resideInAPackage("..outbound..")
                .should().onlyHaveDependentClassesThat()
                .resideInAnyPackage("..outbound..", "..bootstrap..")
                .because("architecture.definition.md 6 rule 5: outbound.* implementations must "
                        + "not be referenced as concrete types outside bootstrap. Previously "
                        + "this checked outbound.integration only, leaving the jOOQ "
                        + "repositories and mappers unguarded, and carried a *Mapper "
                        + "exemption no document grants.")
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
