package com.example.contractmanagement.architecture

import com.example.contractmanagement.architecture.ArchitectureRoot.ROOT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The expiry date on [whileTheContractSliceIsIncomplete].
 *
 * Every rule still carrying that allowance is waiting on one of four packages to exist:
 * `inbound.driver`, `core.inport`, `core.outport` or `inbound.graphql`. When all four are
 * populated, every relaxed rule has a subject and the allowance must go.
 *
 * **The trigger is the conjunction of all four, deliberately, and not "the last layer".**
 * An earlier version keyed on the first class under `contract..outbound..`, reasoning that
 * persistence is last in `tdd.definition.md` § 3's inside-out ordering so everything else
 * would already exist. `ddd-hex-reviewer` pointed out that § 3 *permits justified deviation*
 * from that ordering — a persistence-first increment is explicitly allowed — and five
 * GraphQL-keyed rules would then still be subjectless when the tripwire fired. That is
 * exactly the failure that sank `adr/0026`: a precondition that is true of the intended path
 * rather than of every path. Naming the four packages removes the dependency on ordering.
 *
 * Note that `contract..outbound..` is **not** among them. No relaxed rule is waiting on it:
 * `DependencyRulesTest.rule5_outboundIsReferencedOnlyByBootstrap` already has `shared.outbound`
 * as its subject and carries no allowance.
 *
 * **When this test fails, the slice is complete. Do not "fix" it — retire it:**
 *
 * 1. `grep -rn whileTheContractSliceIsIncomplete src/test` and delete every call.
 * 2. Delete `ContractSliceAllowance.kt` and this file.
 * 3. Restore `isFailOnNoMatchingTests` on the `integrationTest` task in `build.gradle.kts`.
 *    It was disabled because no `*IT` existed, and it is the one part of this arrangement
 *    that lives outside `src/test`, so it is the one most likely to be missed.
 * 4. Run `./gradlew check`. Any rule that now fails is a real violation in the new context,
 *    which is the whole point of the arrangement.
 *
 * The registry-parse assertion the previous tripwire carried is not repeated here:
 * `ContextRegistryTest.theRegistryIsParsedAndPlausible` and
 * `ContextRegistryTest.topLevelPackagesMatchTheRegistry` already own it, and one fact should
 * have one owner.
 *
 * SDD: see `documentation/adr/0027-a-rule-with-no-possible-subject-is-deleted-not-allowed-to-pass-empty.adr.md`.
 */
class ContractSliceTripwireTest {
    private companion object {
        /**
         * The packages the relaxed rules are waiting on. Each is a `.that()` subject of at
         * least one rule still marked [whileTheContractSliceIsIncomplete].
         */
        val AWAITED_PACKAGES =
            listOf(".inbound.driver", ".core.inport", ".core.outport", ".inbound.graphql")
    }

    @Test
    @DisplayName("The contract-slice allowance still has a reason — retire it when this fails")
    fun theContractSliceIsStillMissingAtLeastOneLayer() {
        val populated =
            ArchitectureRoot
                .productionClasses()
                .asSequence()
                .map { it.packageName }
                .filter { it.startsWith("$ROOT.contract.") }
                .toList()

        val stillMissing = AWAITED_PACKAGES.filter { awaited -> populated.none { it.contains(awaited) } }

        assertThat(stillMissing)
            .describedAs(
                "The contract slice is complete: every package the relaxed architecture rules " +
                    "were waiting on now holds production code, so each rule has a subject and " +
                    "must stop being allowed to pass empty. Delete every call to " +
                    "whileTheContractSliceIsIncomplete, delete ContractSliceAllowance.kt and " +
                    "this test, restore isFailOnNoMatchingTests in build.gradle.kts, then " +
                    "re-run: whatever fails next is a real violation the allowance was hiding.",
            ).isNotEmpty()
    }
}
