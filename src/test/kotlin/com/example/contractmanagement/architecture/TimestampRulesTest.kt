package com.example.contractmanagement.architecture

import com.tngtech.archunit.core.domain.JavaClasses
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
 * It is keyed to **both** adapter packages rather than to `inbound.rest`, which is what stops
 * the next transport from repeating the gap that produced this test: § 8.1's table once
 * enumerated `inbound.rest` alone, and the ADR that made GraphQL the only transport left the
 * one rule about timestamps naming the one adapter the service no longer had. Nothing was
 * violated; the rule simply stopped reaching anything. Adding a transport means adding its
 * package here.
 *
 * **This test carried an allowlist and no longer does.** `StartTourRequest.startedAt` was a
 * REST request field of type `Instant` — exactly the pattern § 8.1 forbids — excluded by name
 * and guarded by a companion test asserting the exclusion still had a subject, so that the
 * workaround could not outlive the violation. Deleting the tour-booking example removed the
 * class, that companion test failed on the next run as designed, and both it and the
 * allowlist were deleted rather than repaired. The arrangement worked: an exception that
 * expires on its own is the only kind worth granting, and this is the worked example of one
 * expiring. [ContractSliceAllowance] is the same shape applied to a different workaround.
 *
 * SDD: see `documentation/adr/0007-archunit-boundary-enforcement.adr.md` and
 * `documentation/adr/0014-quality-gates-are-executable.adr.md`.
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
    }

    @Test
    @DisplayName("§ 8.1: no REST request or GraphQL input type carries a timestamp")
    fun noTimestampFieldOnExternalTransportInputTypes() {
        noClasses()
            .that().resideInAnyPackage(*EXTERNAL_TRANSPORT_PACKAGES)
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
                    "caller's claim",
            ).whileTheContractSliceIsIncomplete()
            .check(production)
    }
}
