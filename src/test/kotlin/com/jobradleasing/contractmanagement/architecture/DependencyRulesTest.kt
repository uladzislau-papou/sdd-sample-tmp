package com.jobradleasing.contractmanagement.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Configuration

/**
 * Enforces the seven dependency rules in `architecture.definition.md` § 6, plus
 * the framework-containment rules in § 4.1/§ 4.6 that this stack (JPA, Spring
 * Data, GraphQL) makes easiest to break by accident.
 *
 * SDD: See `documentation/adr/0007-archunit-boundary-enforcement.adr.md`.
 */
class DependencyRulesTest {
    private val classes: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.jobradleasing.contractmanagement")

    /** § 6 rule 1: `core.domain` depends on nothing else (besides `shared.domain` and the JDK). */
    @Test
    fun coreDomain_dependsOnNothingElse() {
        classes()
            .that()
            .resideInAPackage("..core.domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..core.domain..",
                "..shared.domain..",
                "java..",
                "kotlin..",
                "org.jetbrains.annotations..",
            ).check(classes)
    }

    /**
     * § 6 rule 2: `core.inport` and `core.outport` are framework-free. Widened to
     * `core.domain` and `shared.domain`/`shared.outport`: § 9 states "the
     * framework-free constraint applies to `shared.domain` and `shared.outport`,
     * exactly as it applies to a context's `core`" — without this, `ClockPort` and
     * `DomainEventPublisher` (both in `shared.outport`) were unguarded.
     */
    @Test
    fun coreAndSharedKernel_areFrameworkFree() {
        noClasses()
            .that()
            .resideInAnyPackage(
                "..core.domain..",
                "..core.inport..",
                "..core.outport..",
                "..shared.domain..",
                "..shared.outport..",
            ).should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "graphql..")
            .check(classes)
    }

    /**
     * `coding-style.definition.md` § 1.4: `java.util.Optional` must not appear in
     * `core.*` or `shared.*`. `master-leasing-contract-repository.outport.spec.md`
     * § 1 names this exact test as its enforcement.
     */
    @Test
    fun optional_doesNotAppearInCoreOrShared() {
        noClasses()
            .that()
            .resideInAnyPackage("..core..", "..shared..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.Optional")
            .check(classes)
    }

    /**
     * § 6 rule 3 (load-bearing part): `inbound.graphql` must not reach into
     * `core.outport`, `inbound.driver`, or any `outbound.*` implementation.
     * `architecture.definition.md` § 6 rule 3 also permits `core.domain.<aggregate>.exception`
     * and `core.domain` enums referenced by an inport command — this rule does not
     * re-enumerate the positive allow-list, only the load-bearing prohibitions.
     */
    @Test
    fun inboundGraphql_doesNotReachOutportDriverOrOutbound() {
        noClasses()
            .that()
            .resideInAPackage("..inbound.graphql..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..core.outport..", "..inbound.driver..", "..outbound..")
            .check(classes)
    }

    /** § 6 rule 4: `inbound.driver` depends on `core.domain`, `core.inport`, `core.outport` only. */
    @Test
    fun inboundDriver_dependsOnlyOnCoreAndSharedOutport() {
        classes()
            .that()
            .resideInAPackage("..inbound.driver..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..core.domain..",
                "..core.inport..",
                "..core.outport..",
                "..inbound.driver..",
                "..shared.domain..",
                "..shared.outport..",
                "java..",
                "kotlin..",
                "org.jetbrains.annotations..",
            ).check(classes)
    }

    /** § 6 rule 5: `outbound.*` implementations must not be referenced as concrete types from adapters. */
    @Test
    fun inbound_doesNotDependOnOutboundConcreteTypes() {
        noClasses()
            .that()
            .resideInAPackage("..inbound..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..outbound..")
            .check(classes)
    }

    /** § 6 rule 6: no persistence type crosses into the core or the shared kernel. */
    @Test
    fun persistenceTypes_doNotCrossIntoCoreOrShared() {
        noClasses()
            .that()
            .resideInAnyPackage("..core..", "..shared.domain..", "..shared.outport..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
            .check(classes)
    }

    /**
     * § 6 rule 7: only `bootstrap` wires implementations. `allowEmptyShould` is
     * deliberate: no `@Configuration` class exists yet (no adapter needs wiring
     * until the persistence increment), and the rule should hold vacuously rather
     * than fail on absence — it starts protecting the moment one is added.
     */
    @Test
    fun onlyBootstrap_declaresSpringConfiguration() {
        classes()
            .that()
            .areAnnotatedWith(Configuration::class.java)
            .should()
            .resideInAPackage("..bootstrap..")
            .allowEmptyShould(true)
            .check(classes)
    }
}
