package com.example.contractmanagement.architecture

import com.example.contractmanagement.architecture.ArchitectureRoot.ROOT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The executable owner of `adr/0027`'s **restoration** instruction.
 *
 * `adr/0027` deleted five architecture rules whose subject the service cannot currently have:
 * four describing `inbound.rest` and one describing `inbound.listener`. `adr/0020` makes
 * GraphQL the only transport, and no use case in `uc01`–`uc06` is listener-driven, so an
 * allowance for them could never expire and deletion was chosen instead.
 *
 * Deletion is only honest if the day their subject appears is a day the build says so.
 * As first written, `adr/0027` said the rules are restored "in the increment that adds the
 * transport" and left it at that — prose, in an ADR whose entire argument is that prose is
 * the thing that rots. `ddd-hex-reviewer` caught it as `adr/0026`'s defect one population
 * over: 0026 had a *retirement* precondition that could not fire, 0027 had a *restoration*
 * precondition that did not exist. This test is that precondition.
 *
 * `ContextRegistryTest` does not cover this and cannot be made to cheaply:
 * `topLevelPackagesMatchTheRegistry` compares only the **top-level** segment, so
 * `contract.inbound.rest` sits inside the already-registered `contract` package and passes,
 * and `everyContextFollowsTheSameOntology` permits `inbound` wholesale.
 *
 * **When this test fails, restore the rules it names — do not delete it.** Recover them from
 * `adr/0027`, which lists all five, or from this file's history. Their specifications never
 * went anywhere: `coding-style.definition.md` § 3.3, `architecture.definition.md` § 4.5,
 * § 4.8 and § 6 rule 3. Delete this test only once all five are back and passing.
 *
 * SDD: see `documentation/adr/0027-a-rule-with-no-possible-subject-is-deleted-not-allowed-to-pass-empty.adr.md`
 * and `documentation/adr/0014-quality-gates-are-executable.adr.md`.
 */
class DeletedTransportRulesTripwireTest {
    private companion object {
        /** Adapter packages whose rules `adr/0027` deleted, mapped to what must come back. */
        val DELETED_RULE_OWNERS =
            mapOf(
                ".inbound.rest" to
                    "restAdapterCarriesNoHttpAnnotation, deliveryDtosCarryNoDomainType, " +
                    "the *RestController half of deliveryAdaptersAreRegisteredWithTheirFramework, " +
                    "and DependencyRulesTest.rule3_restDependsOnInportOnly",
                ".inbound.listener" to "listenersDoNotTouchOutboundAdapters",
            )
    }

    @Test
    @DisplayName("The rules adr/0027 deleted still have no subject — restore them when this fails")
    fun noAdapterExistsForTheRulesThatWereDeleted() {
        val packages = ArchitectureRoot.productionClasses().asSequence().map { it.packageName }.toList()

        val resurrected =
            DELETED_RULE_OWNERS
                .filterKeys { adapter -> packages.any { it.startsWith(ROOT) && it.contains(adapter) } }
                .map { (adapter, rules) -> "$adapter now exists — restore: $rules" }

        assertThat(resurrected)
            .describedAs(
                "A transport whose architecture rules adr/0027 deleted has just appeared. The " +
                    "rules were removed because they had no possible subject, on the written " +
                    "condition that they come back in the increment that gives them one — this " +
                    "is that increment. Restore the rules listed above (adr/0027 names all " +
                    "five), then delete this test. Do not delete it to make the build green.",
            ).isEmpty()
    }
}
