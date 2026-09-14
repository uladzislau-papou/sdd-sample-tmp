package com.jobradleasing.contractmanagement.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val BASE_PACKAGE = "com.jobradleasing.contractmanagement"

/**
 * Enforces `architecture.definition.md` § 11: the bounded-context registry is a
 * checkable fact, not a matter of opinion.
 *
 * SDD: See `documentation/adr/0007-archunit-boundary-enforcement.adr.md`.
 */
class ContextRegistryTest {
    private val importedClasses: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE)

    /**
     * ADR 0007's consequences: a silent import failure would make every other
     * rule in this suite pass vacuously, which is more dangerous than no suite
     * at all. This is the tripwire.
     */
    @Test
    fun importer_findsProductionClasses() {
        assertThat(importedClasses.size).isGreaterThan(20)
    }

    @Test
    fun topLevelPackages_areRegistered() {
        val registered = setOf("bootstrap", "masterleasing", "individualleasing", "shared")
        val prefix = "$BASE_PACKAGE."

        val actual =
            importedClasses
                .map { it.packageName }
                .filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix).substringBefore('.') }
                .toSet()

        assertThat(actual).isSubsetOf(registered)
    }

    /**
     * `architecture.definition.md` § 9 rule 3 and § 11 rule 4: `shared` must not
     * depend on any bounded context. `coding-style.definition.md` § 6.2 names this
     * exact test as the enforcement behind the shared-kernel exemption
     * (`Money`/`EmployerId`/`LessorId` throwing `IllegalArgumentException` rather
     * than a domain exception, because `shared` structurally cannot reach one).
     */
    @Test
    fun shared_dependsOnNoBoundedContext() {
        noClasses()
            .that()
            .resideInAPackage("..shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..masterleasing..", "..individualleasing..")
            .check(importedClasses)
    }

    /**
     * `architecture.definition.md` § 11 rule 3: cross-context communication to
     * another context's inport is ordinary orchestration and belongs to the
     * orchestrating driver (§ 4.4), never to a controller or a listener reaching
     * in directly. `cancel-master-leasing-contract.inport.spec.md` § (UC04) and
     * `terminate-contracts-by-master-contract.inport.spec.md` (UC08) name this test
     * verbatim as their enforcement. Vacuous today — `individualleasing` does not
     * exist yet — and starts protecting the moment UC04's driver is built.
     */
    @Test
    fun masterLeasingReachesIndividualLeasingInport_onlyFromADriver() {
        noClasses()
            .that()
            .resideInAPackage("..masterleasing..")
            .and()
            .resideOutsideOfPackage("..masterleasing.inbound.driver..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..individualleasing.core.inport..")
            .check(importedClasses)
    }

    /**
     * The symmetric case: `issue-individual-leasing-contract.inport.spec.md`
     * (UC05) and `read-leasing-terms.inport.spec.md` (UC09) name this test
     * verbatim. Vacuous today for the same reason as above.
     */
    @Test
    fun individualLeasingReachesMasterLeasingInport_onlyFromADriver() {
        noClasses()
            .that()
            .resideInAPackage("..individualleasing..")
            .and()
            .resideOutsideOfPackage("..individualleasing.inbound.driver..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..masterleasing.core.inport..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    /**
     * `architecture.definition.md` § 11 rule 3: everything except a context's
     * inport is closed to the other context. `read-leasing-terms.inport.spec.md`
     * names this test verbatim. Vacuous today for the same reason as above.
     */
    @Test
    fun individualLeasing_doesNotImportMasterLeasingInternals() {
        noClasses()
            .that()
            .resideInAPackage("..individualleasing..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..masterleasing.core.domain..",
                "..masterleasing.core.outport..",
                "..masterleasing.inbound..",
                "..masterleasing.outbound..",
            ).allowEmptyShould(true)
            .check(importedClasses)
    }

    /**
     * `aggregate-master-leasing-contract.spec.md` § 4 (`reconstitute`):
     * "persistence mapper only ... must stay public because the mapper lives in
     * another package, so visibility cannot express the restriction". Named
     * verbatim there as this test's job. Matches by method name rather than by a
     * concrete target, so it protects from the day `reconstitute` is first written
     * — it does not need the method to exist yet to be a real (non-vacuous)
     * `noClasses` rule.
     */
    @Test
    fun reconstitute_isCalledOnlyByPersistenceMappers() {
        val callsReconstitute =
            object : DescribedPredicate<JavaMethodCall>("calls a method named 'reconstitute'") {
                override fun test(call: JavaMethodCall) = call.target.name == "reconstitute"
            }

        noClasses()
            .that()
            .resideOutsideOfPackage("..outbound.persistence..")
            .should()
            .callMethodWhere(callsReconstitute)
            .check(importedClasses)
    }
}
