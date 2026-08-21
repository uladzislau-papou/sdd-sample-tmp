package com.dominikgaller.alpinebooking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Mechanical enforcement of class roles and domain purity.
 *
 * <p>Sources: {@code architecture.definition.md} section 4.5 (REST split), section 8
 * (time), section 4.4 (drivers), section 4.8 (listeners);
 * {@code coding-style.definition.md} section 3.3.
 *
 * <p>The {@code *RestAPI}/{@code *Controller} annotation split is the rule a planted-drift
 * test proved breaks silently: moving {@code @PostMapping} onto the controller relocated
 * the route and failed four unrelated tests with no hint of the cause.
 *
 * <p>SDD: See {@code documentation/adr/0007-archunit-boundary-enforcement.adr.md}.
 */
class ClassRoleRulesTest {

    private static final String ROOT = "com.dominikgaller.alpinebooking";
    private static final String MVC = "org.springframework.web.bind.annotation";

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(location -> !location.contains("/jooq/"))
                .importPackages(ROOT);
    }

    // ── REST split (section 4.5) ─────────────────────────────────────────────

    @Test
    @DisplayName("Section 4.5: no HTTP mapping annotation on a *Controller method")
    void controllers_carryNoHttpMappingAnnotations() {
        noMethods()
                .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(MVC + ".RequestMapping")
                .orShould().beAnnotatedWith(MVC + ".GetMapping")
                .orShould().beAnnotatedWith(MVC + ".PostMapping")
                .orShould().beAnnotatedWith(MVC + ".PatchMapping")
                .orShould().beAnnotatedWith(MVC + ".PutMapping")
                .orShould().beAnnotatedWith(MVC + ".DeleteMapping")
                .orShould().beAnnotatedWith(MVC + ".ResponseStatus")
                .because("architecture.definition.md 4.5: all Spring MVC annotations belong "
                        + "on the *RestAPI interface. A *Controller carries only "
                        + "@RestController, implements *RestAPI, and @Override methods. "
                        + "Duplicating a mapping here silently relocates the route.")
                .check(production);
    }

    @Test
    @DisplayName("Section 4.5: no class-level @RequestMapping on a *Controller")
    void controllers_carryNoClassLevelRequestMapping() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(MVC + ".RequestMapping")
                .because("architecture.definition.md 4.5: the base path belongs on the "
                        + "*RestAPI interface.")
                .check(production);
    }

    @Test
    @DisplayName("Section 4.5: every *Controller is a @RestController")
    void controllers_areAnnotatedAsRestController() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(MVC + ".RestController")
                .because("architecture.definition.md 4.5.")
                .check(production);
    }

    // ── Domain purity ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Section 8: the domain never reads the clock directly")
    void domain_neverCallsNowDirectly() {
        noClasses()
                .that().resideInAnyPackage("..core..", "..shared.domain..")
                .should().callMethod(java.time.Instant.class, "now")
                .orShould().callMethod(java.time.LocalDate.class, "now")
                .orShould().callMethod(java.time.LocalDateTime.class, "now")
                .because("architecture.definition.md 8: time enters through ClockPort or is "
                        + "passed in. SystemClockPort is the only permitted caller.")
                .check(production);
    }

    @Test
    @DisplayName("Section 4.1: no Spring or Jakarta annotation in core.domain")
    void coreDomain_isFrameworkFree() {
        noClasses()
                .that().resideInAPackage("..core.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "com.fasterxml..")
                .because("architecture.definition.md 4.1: the domain model MUST NOT use "
                        + "Spring, Jakarta, Jackson or any persistence framework.")
                .check(production);
    }

    // ── Driver and listener roles ────────────────────────────────────────────

    @Test
    @DisplayName("Section 4.4: drivers carry no delivery-side annotations")
    void drivers_carryNoDeliveryAnnotations() {
        noClasses()
                .that().resideInAPackage("..inbound.driver..")
                .should().beAnnotatedWith(MVC + ".RestController")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                .orShould().beAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
                .because("architecture.definition.md 4.4: a driver is an application "
                        + "service. Delivery concerns belong to inbound.rest or "
                        + "inbound.listener.")
                .check(production);
    }

    @Test
    @DisplayName("Section 4.8: listeners do not touch outbound adapters")
    void listeners_doNotDependOnOutboundAdapters() {
        noClasses()
                .that().resideInAPackage("..inbound.listener..")
                .should().dependOnClassesThat().resideInAPackage("..outbound..")
                .because("architecture.definition.md 4.8: a listener forwards into inports "
                        + "or outports; it must not reach an implementation.")
                .check(production);
    }

    // ── Naming conventions (coding-style.definition.md section 4) ────────────

    @Test
    @DisplayName("Naming: inport.command holds only *Command types")
    void inportCommand_holdsOnlyCommands() {
        classes()
                .that().resideInAPackage("..core.inport.command..")
                .should().haveSimpleNameEndingWith("Command")
                .check(production);
    }

    @Test
    @DisplayName("Naming: inport.result holds only *Result types")
    void inportResult_holdsOnlyResults() {
        classes()
                .that().resideInAPackage("..core.inport.result..")
                .should().haveSimpleNameEndingWith("Result")
                .check(production);
    }

    @Test
    @DisplayName("Naming: inport.usecase holds only *UseCase interfaces")
    void inportUsecase_holdsOnlyUseCaseInterfaces() {
        classes()
                .that().resideInAPackage("..core.inport.usecase..")
                .should().haveSimpleNameEndingWith("UseCase")
                .andShould().beInterfaces()
                .check(production);
    }

    @Test
    @DisplayName("Naming: inbound.driver holds only *Driver classes")
    void inboundDriver_holdsOnlyDrivers() {
        classes()
                .that().resideInAPackage("..inbound.driver..")
                .should().haveSimpleNameEndingWith("Driver")
                .check(production);
    }

    @Test
    @DisplayName("Naming: rest.request and rest.response hold only their DTOs")
    void restDtos_areNamedConsistently() {
        classes()
                .that().resideInAPackage("..inbound.rest.request..")
                .should().haveSimpleNameEndingWith("Request")
                .check(production);

        classes()
                .that().resideInAPackage("..inbound.rest.response..")
                .should().haveSimpleNameEndingWith("Response")
                .check(production);
    }

    @Test
    @DisplayName("Section 4.5: no domain type appears in a REST DTO")
    void restDtos_doNotExposeDomainTypes() {
        noClasses()
                .that().resideInAnyPackage("..inbound.rest.request..", "..inbound.rest.response..")
                .should().dependOnClassesThat().resideInAPackage("..core.domain..")
                .because("architecture.definition.md 4.5: no domain object in a public DTO, "
                        + "so the API contract can stay stable while the model evolves.")
                .check(production);
    }

    @Test
    @DisplayName("Every *UseCase implementation is a driver")
    void useCaseImplementations_liveInInboundDriver() {
        classes()
                .that().implement(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "a *UseCase interface",
                        javaClass -> javaClass.getSimpleName().endsWith("UseCase")))
                .should().resideInAPackage("..inbound.driver..")
                .because("architecture.definition.md 4.4: use case implementations are "
                        + "application services and live in inbound.driver.")
                .check(production);
    }

    @Test
    @DisplayName("Methods on a *RestAPI interface are not implemented there")
    void restApi_isAnInterface() {
        classes()
                .that().haveSimpleNameEndingWith("RestAPI")
                .should().beInterfaces()
                .because("architecture.definition.md 4.5: *RestAPI is the HTTP contract.")
                .check(production);
    }

    /**
     * {@code reconstitute} deliberately skips every creation-time invariant
     * ({@code modelling.definition.md}, Rehydration Rule), so it is a hole in the
     * Always-Valid guarantee for anything that is not the persistence mapper. It must be
     * {@code public} — the mappers live in a different package — so visibility cannot
     * express the restriction and a rule has to.
     *
     * <p>Test code calls it freely as a builder; that is fine, and invisible here because
     * the importer excludes tests.
     */
    @Test
    @DisplayName("Only the persistence mappers may call reconstitute")
    void reconstitute_isCalledOnlyByPersistenceMappers() {
        noClasses()
                .that().resideOutsideOfPackage("..outbound.persistence..")
                .should().callMethodWhere(
                        com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                com.tngtech.archunit.core.domain.properties.HasName.Predicates
                                        .name("reconstitute")))
                .because("reconstitute bypasses the invariants that request(...) and "
                        + "schedule(...) enforce. Only the persistence mapper rehydrating a "
                        + "row that was valid when written may use it — application code "
                        + "must go through the guarded factories.")
                .check(production);
    }

    @Test
    @DisplayName("Repository outports expose no persistence type")
    void outports_exposeNoPersistenceTypes() {
        methods()
                .that().areDeclaredInClassesThat().resideInAPackage("..core.outport..")
                .should().notHaveRawReturnType(
                        com.tngtech.archunit.base.DescribedPredicate.describe(
                                "a generated jOOQ type",
                                javaClass -> javaClass.getPackageName().startsWith(ROOT + ".jooq")))
                .because("architecture.definition.md 6 rule 6.")
                .check(production);
    }
}
