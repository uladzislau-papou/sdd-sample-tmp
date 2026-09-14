package com.jobradleasing.contractmanagement.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Enforces `coding-style.definition.md` § 4.1 naming/role conventions and the
 * domain-purity rules in `architecture.definition.md` § 8.
 *
 * SDD: See `documentation/adr/0007-archunit-boundary-enforcement.adr.md`.
 */
class ClassRoleRulesTest {
    private val classes: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.jobradleasing.contractmanagement")

    /**
     * `@MutationMapping`/`@QueryMapping` are `@Target(METHOD)` only — never
     * applicable to a class — so those two MUST be checked at the method level. A
     * class-level `areAnnotatedWith` predicate for them would be unsatisfiable by
     * construction and pass vacuously (ADR 0007 § Consequences: "a silent import
     * failure ... produces a green tick"). `@Argument` is not checked separately:
     * it is only meaningful on a parameter of a method already constrained here.
     */
    @Test
    fun graphQlMappingMethods_onlyInInboundGraphql() {
        methods()
            .that()
            .areAnnotatedWith(MutationMapping::class.java)
            .or()
            .areAnnotatedWith(QueryMapping::class.java)
            .or()
            .areAnnotatedWith(SchemaMapping::class.java)
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage("..inbound.graphql..")
            .check(classes)
    }

    /**
     * `@Controller` is `@Target(TYPE)` only. `@SchemaMapping` is
     * `@Target({TYPE, METHOD})` — its class-level placement is checked here
     * alongside `@Controller`; its method-level placement is checked above.
     */
    @Test
    fun classLevelGraphQlAnnotations_onlyInInboundGraphql() {
        classes()
            .that()
            .areAnnotatedWith(Controller::class.java)
            .or()
            .areAnnotatedWith(SchemaMapping::class.java)
            .should()
            .resideInAPackage("..inbound.graphql..")
            .check(classes)
    }

    @Test
    fun inportCommands_endWithCommandSuffix() {
        classes()
            .that()
            .resideInAPackage("..core.inport.command..")
            .should()
            .haveSimpleNameEndingWith("Command")
            .check(classes)
    }

    @Test
    fun inportResults_endWithResultSuffix() {
        classes()
            .that()
            .resideInAPackage("..core.inport.result..")
            .should()
            .haveSimpleNameEndingWith("Result")
            .check(classes)
    }

    @Test
    fun inportUseCases_endWithUseCaseSuffixAndAreInterfaces() {
        classes()
            .that()
            .resideInAPackage("..core.inport.usecase..")
            .should()
            .haveSimpleNameEndingWith("UseCase")
            .andShould()
            .beInterfaces()
            .check(classes)
    }

    @Test
    fun drivers_endWithDriverSuffix() {
        classes()
            .that()
            .resideInAPackage("..inbound.driver..")
            .should()
            .haveSimpleNameEndingWith("Driver")
            .check(classes)
    }

    @Test
    fun graphqlInputTypes_endWithInputSuffix() {
        classes()
            .that()
            .resideInAPackage("..inbound.graphql.input..")
            .should()
            .haveSimpleNameEndingWith("Input")
            .check(classes)
    }

    @Test
    fun graphqlPayloadTypes_endWithPayloadSuffix() {
        classes()
            .that()
            .resideInAPackage("..inbound.graphql.payload..")
            .should()
            .haveSimpleNameEndingWith("Payload")
            .check(classes)
    }

    /**
     * `architecture.definition.md` § 8: the domain must never call `now()`
     * directly. `shared.outbound.clock.SystemClockPort` is the sole sanctioned
     * caller (`documentation/ports/clock.outport.spec.md` § 4) — it does not exist
     * yet, so this rule currently protects the whole tree.
     */
    @Test
    fun clockNowMethods_areNotCalledOutsideSystemClockPort() {
        noClasses()
            .that()
            .resideOutsideOfPackage("..shared.outbound.clock..")
            .should()
            .callMethod(Instant::class.java, "now")
            .orShould()
            .callMethod(LocalDate::class.java, "now")
            .orShould()
            .callMethod(LocalDateTime::class.java, "now")
            .check(classes)
    }
}
