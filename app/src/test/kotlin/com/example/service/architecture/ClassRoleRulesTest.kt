package com.example.service.architecture

import com.example.service.architecture.ArchitectureRoot.ROOT
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaAnnotation
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Executable form of the class-role rules in `coding-style.definition.md` § 3.3 and § 4.1.
 *
 * A role here is decided by a class's **name**, which is why the transports are named
 * explicitly (`*RestController`, `*GraphQLController`) rather than sharing `*Controller`:
 * Spring for GraphQL annotates its resolvers `@Controller` too, and a rule that cannot
 * tell two roles apart cannot enforce either.
 *
 * SDD: see `documentation/adr/0007-archunit-boundary-enforcement.adr.md`.
 */
class ClassRoleRulesTest {
    companion object {
        private const val MVC = "org.springframework.web.bind.annotation"
        private const val GRAPHQL_MAPPING = "org.springframework.graphql.data.method.annotation"

        private lateinit var production: JavaClasses

        @BeforeAll
        @JvmStatic
        fun importProductionClasses() {
            production = ArchitectureRoot.productionClasses()
        }

        /** An annotation declared in [packagePrefix] or below. */
        private fun annotationFrom(packagePrefix: String) =
            object : DescribedPredicate<JavaAnnotation<*>>("an annotation from $packagePrefix") {
                override fun test(annotation: JavaAnnotation<*>): Boolean =
                    annotation.rawType.packageName.startsWith(packagePrefix)
            }
    }

    @Test
    @DisplayName("The delivery contract carries the annotations, the adapter carries none: REST")
    fun restAdapterCarriesNoHttpAnnotation() {
        noMethods()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("RestController")
            .should(
                object : ArchCondition<JavaMethod>("carry an HTTP mapping annotation") {
                    override fun check(
                        method: JavaMethod,
                        events: ConditionEvents,
                    ) {
                        val violating = method.annotations.filter { it.rawType.packageName.startsWith(MVC) }
                        events.add(
                            SimpleConditionEvent(
                                method,
                                violating.isNotEmpty(),
                                "${method.fullName} is annotated with ${violating.map { it.rawType.simpleName }}",
                            ),
                        )
                    }
                },
            ).because(
                "coding-style.definition.md 3.3: the whole HTTP surface lives on the " +
                    "*RestAPI interface, so the wire contract can be read in one file.",
            ).check(production)
    }

    @Test
    @DisplayName("The delivery contract carries the annotations, the adapter carries none: GraphQL")
    fun graphqlAdapterCarriesNoMappingAnnotation() {
        noMethods()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("GraphQLController")
            .should(
                object : ArchCondition<JavaMethod>("carry a GraphQL mapping annotation") {
                    override fun check(
                        method: JavaMethod,
                        events: ConditionEvents,
                    ) {
                        val violating =
                            method.annotations.filter { it.rawType.packageName.startsWith(GRAPHQL_MAPPING) }
                        events.add(
                            SimpleConditionEvent(
                                method,
                                violating.isNotEmpty(),
                                "${method.fullName} is annotated with ${violating.map { it.rawType.simpleName }}",
                            ),
                        )
                    }
                },
            ).because(
                "coding-style.definition.md 3.3: the GraphQL split mirrors the REST one. " +
                    "That the annotations are found on the interface at all was verified " +
                    "empirically, not assumed.",
            ).check(production)
    }

    @Test
    @DisplayName("Every *RestController is a @RestController and every *GraphQLController a @Controller")
    fun deliveryAdaptersAreRegisteredWithTheirFramework() {
        classes()
            .that().haveSimpleNameEndingWith("RestController")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .because("a delivery adapter Spring never registers is dead code that still compiles.")
            .check(production)

        classes()
            .that().haveSimpleNameEndingWith("GraphQLController")
            .should().beAnnotatedWith("org.springframework.stereotype.Controller")
            .because("Spring for GraphQL discovers resolvers by @Controller and nothing else.")
            .check(production)
    }

    @Test
    @DisplayName("Section 8: the domain never reads the clock directly")
    fun domainNeverReadsTheClockDirectly() {
        noClasses()
            .that().resideInAnyPackage("..core.domain..", "..shared.domain..")
            .should().callMethod(java.time.Instant::class.java, "now")
            .orShould().callMethod(java.time.LocalDate::class.java, "now")
            .because(
                "architecture.definition.md 8: time enters through ClockPort at the driver " +
                    "and reaches the domain as a parameter. A domain that reads a clock has " +
                    "invariants no test can pin down.",
            ).check(production)
    }

    @Test
    @DisplayName("Section 4.1: no Spring or Jakarta annotation in core.domain")
    fun domainCarriesNoFrameworkAnnotation() {
        noClasses()
            .that().resideInAnyPackage("..core.domain..", "..shared.domain..")
            .should().beAnnotatedWith(
                object : DescribedPredicate<JavaAnnotation<*>>("a Spring or Jakarta annotation") {
                    override fun test(annotation: JavaAnnotation<*>): Boolean =
                        annotation.rawType.packageName.startsWith("org.springframework") ||
                            annotation.rawType.packageName.startsWith("jakarta")
                },
            ).because("architecture.definition.md 4.1: the domain is framework-free.")
            .check(production)
    }

    @Test
    @DisplayName("Section 4.4: drivers carry no delivery-side annotations")
    fun driversCarryNoDeliveryAnnotation() {
        noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..inbound.driver..")
            .should().beAnnotatedWith(annotationFrom(MVC))
            .orShould().beAnnotatedWith(annotationFrom(GRAPHQL_MAPPING))
            .because(
                "architecture.definition.md 4.4: a driver is reached through its inbound " +
                    "port. A driver that maps a route is an adapter wearing the wrong name, " +
                    "and it silently ties the use case to one transport.",
            ).check(production)
    }

    @Test
    @DisplayName("Section 4.8: listeners do not touch outbound adapters")
    fun listenersDoNotTouchOutboundAdapters() {
        noClasses()
            .that().resideInAPackage("..inbound.listener..")
            .should().dependOnClassesThat().resideInAPackage("..outbound..")
            .because("architecture.definition.md 4.8: a listener is an inbound adapter like any other.")
            .check(production)
    }

    @Test
    @DisplayName("Naming: each core package holds only the role it is named for")
    fun corePackagesHoldOnlyTheirRole() {
        classes().that().resideInAPackage("..core.inport.command..")
            .should().haveSimpleNameEndingWith("Command")
            .because("coding-style.definition.md 4.1").check(production)

        classes().that().resideInAPackage("..core.inport.result..")
            .should().haveSimpleNameEndingWith("Result")
            .because("coding-style.definition.md 4.1").check(production)

        classes().that().resideInAPackage("..core.inport.usecase..")
            .should().haveSimpleNameEndingWith("UseCase")
            .andShould().beInterfaces()
            .because("coding-style.definition.md 4.1: an inbound port is an interface.")
            .check(production)

        classes().that().resideInAPackage("..inbound.driver..")
            .should().haveSimpleNameEndingWith("Driver")
            .because("coding-style.definition.md 4.1").check(production)
    }

    @Test
    @DisplayName("Section 4.5: no domain type appears in a delivery DTO")
    fun deliveryDtosCarryNoDomainType() {
        noClasses()
            .that().resideInAnyPackage(
                "..inbound.rest.request..",
                "..inbound.rest.response..",
            ).should().dependOnClassesThat().resideInAPackage("..core.domain..")
            .because(
                "architecture.definition.md 4.5: a DTO that carries a domain type makes the " +
                    "wire format an alias of the domain, so neither can change alone.",
            ).check(production)
    }

    @Test
    @DisplayName("Every *UseCase implementation is a driver")
    fun useCaseImplementationsAreDrivers() {
        classes()
            .that().areNotInterfaces()
            .and().implement(DescribedPredicate.describe("a *UseCase port") { it.simpleName.endsWith("UseCase") })
            .should().resideInAPackage("..inbound.driver..")
            .because(
                "architecture.definition.md 4.4: an inbound port is implemented by exactly " +
                    "one kind of class. An adapter implementing it directly would skip the " +
                    "transaction boundary.",
            ).check(production)
    }

    @Test
    @DisplayName("Only the persistence mappers may call reconstitute")
    fun reconstituteIsCalledOnlyByPersistence() {
        noClasses()
            .that().resideOutsideOfPackage("..outbound.persistence..")
            .should().callMethodWhere(
                DescribedPredicate.describe("a call to an aggregate's reconstitute") { call ->
                    call.target.name == "reconstitute" &&
                        call.targetOwner.packageName.startsWith(ROOT) &&
                        call.targetOwner.packageName.contains(".core.domain")
                },
            ).because(
                "reconstitute skips the invariants the factory enforces. It is how a valid " +
                    "past fact is reloaded, not a shortcut for building an aggregate.",
            ).check(production)
    }

    @Test
    @DisplayName("Repository outports expose no persistence type")
    fun repositoryOutportsExposeNoPersistenceType() {
        noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..core.outport..")
            .should().haveRawReturnType(
                DescribedPredicate.describe("a persistence type") { type ->
                    type.packageName.startsWith("jakarta.persistence") ||
                        type.packageName.startsWith("org.springframework.data") ||
                        type.simpleName.endsWith("JpaEntity")
                },
            ).because(
                "an outbound port is the core's vocabulary. A port returning an entity or a " +
                    "Page has already leaked the adapter it was meant to hide.",
            ).check(production)
    }
}
