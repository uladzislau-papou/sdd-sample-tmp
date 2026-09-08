package com.example.contractmanagement.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * Executable form of `architecture.definition.md` § 8.1 — who may supply a domain timestamp.
 *
 * § 8.1's discriminator is **the caller**, not the field: a client of an external transport is
 * asserting a fact about our clock, and there is no business case in this project for that.
 * Its table therefore says `ClockPort` only, with no timestamp on the request DTO or the
 * GraphQL input type.
 *
 * **Why this test exists, which is the interesting part.** § 8.1's table originally enumerated
 * `inbound.rest` alone. `adr/0020-graphql-as-the-only-transport.adr.md` then made GraphQL the
 * *sole* transport — so the one rule about who may supply a timestamp named the one adapter
 * the service no longer had. Nothing was violated; the rule simply stopped reaching anything,
 * and UC07's OPEN QUESTION 11 was raised because no criterion could name where a recorded
 * timestamp came from until it was settled.
 *
 * Reading the existing rule as covering GraphQL was always the sensible reading. But a reading
 * is not enforceable, and ADR-0014's instruction for a rule that has rotted is to give it an
 * executable owner rather than to restate it more firmly. So the row was added *and* keyed to
 * a test.
 *
 * It is keyed to **both** adapter packages rather than to `inbound.rest`, which is what stops
 * the next transport from repeating the gap. Adding one means adding its package here.
 *
 * SDD: see `documentation/adr/0007-archunit-boundary-enforcement.adr.md`,
 * `documentation/adr/0014-quality-gates-are-executable.adr.md`, and
 * `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` PD-11.
 */
class TimestampRulesTest {
    companion object {
        private lateinit var production: JavaClasses

        @BeforeAll
        @JvmStatic
        fun importProductionClasses() {
            production = ArchitectureRoot.productionClasses()
        }

        /**
         * The adapter packages § 8.1's table classes as "external transport".
         *
         * `inbound.listener` is deliberately absent: § 8.1 *requires* a listener to use the
         * event's own timestamp, because that is a fact already recorded in the originating
         * context. Re-dating it with the receiver's clock would make the two drift apart by the
         * event's delivery latency.
         */
        private val EXTERNAL_TRANSPORT_PACKAGES =
            arrayOf("..inbound.rest..", "..inbound.graphql..")

        /**
         * The one pre-existing violation, named rather than hidden.
         *
         * `StartTourRequest.startedAt` is an `Instant` on a REST request body — exactly the
         * pattern § 8.1 was written to end, and it survived because § 8.1 had no executable
         * owner until this test. The rule found it on its first run.
         *
         * **It is a document conflict, not a coding slip.** `uc05-start-tour.spec.md` § 2
         * *specifies* `startedAt` as "an optional request body field", § 3 gives it a
         * `TourStartTooEarlyException` at 409, and § 7 has a criterion asserting the supplied
         * value is used. § 8.1 says the opposite in as many words: "MUST NOT accept one from
         * the request … Nothing needs validating, because nothing is accepted."
         *
         * `CLAUDE.md`'s authority order settles which is wrong:
         * `architecture.definition.md` is rank 2 and a use-case spec is rank 16, so **UC05's
         * spec is wrong and the code follows it**. Fixing it removes a documented feature, its
         * exception path, three of `GuideTourRestControllerTest`'s cases and two requests in
         * `api/uc05-start-tour.http` — an increment of its own, with its own spec change.
         *
         * Excluded here and **not** by loosening the rule, which
         * `test.definition.md` § 7 item 11 forbids outright. The rule still fails on any new
         * violation, including anything in `mlc`. [theKnownViolationStillExists] fails when
         * UC05 is fixed, so this exclusion cannot outlive the thing it excuses.
         */
        private const val KNOWN_VIOLATION =
            "com.example.contractmanagement.guide.inbound.rest.request.StartTourRequest"
    }

    @Test
    @DisplayName("§ 8.1: no REST request or GraphQL input type carries a timestamp")
    fun noTimestampFieldOnExternalTransportInputTypes() {
        noClasses()
            .that().resideInAnyPackage(*EXTERNAL_TRANSPORT_PACKAGES)
            .and().doNotHaveFullyQualifiedName(KNOWN_VIOLATION)
            .should().dependOnClassesThat()
            .haveFullyQualifiedName(Instant::class.java.name)
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName(OffsetDateTime::class.java.name)
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName(ZonedDateTime::class.java.name)
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName(LocalDateTime::class.java.name)
            .because(
                "architecture.definition.md 8.1: a driver behind a REST or GraphQL endpoint " +
                    "MUST read the timestamp from ClockPort and MUST NOT accept one from the " +
                    "request. The time an action happened is the system's observation, not the " +
                    "caller's claim. UC07's PD-11 is the decision that added the GraphQL row",
            ).check(production)
    }

    /**
     * Fails when the exclusion above becomes unnecessary.
     *
     * An allowlist with no expiry is how a temporary exception becomes permanent: the entry
     * stays after the violation is fixed, and the rule quietly stops covering a class nobody
     * remembers excluding. So the exclusion asserts its own subject.
     *
     * **When this test fails, delete it and [KNOWN_VIOLATION] — do not "fix" it.** A failure
     * here means UC05 was corrected and the rule can cover `..inbound.rest..` whole.
     *
     * This is ADR-0014 applied to the workaround rather than only to the rule: the thing that
     * rots is the exception, so the exception is what gets an executable owner.
     */
    @Test
    @DisplayName("The § 8.1 exclusion still has a subject — delete both when this fails")
    fun theKnownViolationStillExists() {
        classes()
            .that().haveFullyQualifiedName(KNOWN_VIOLATION)
            .should().dependOnClassesThat()
            .haveFullyQualifiedName(Instant::class.java.name)
            .because(
                "if StartTourRequest no longer carries an Instant, UC05 was fixed and both " +
                    "KNOWN_VIOLATION and this test must be deleted so the rule covers " +
                    "..inbound.rest.. whole. An allowlist that outlives its violation is how a " +
                    "rule silently stops covering a class",
            ).check(production)
    }
}
