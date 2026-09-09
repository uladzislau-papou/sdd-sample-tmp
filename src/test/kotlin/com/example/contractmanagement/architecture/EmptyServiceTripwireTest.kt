package com.example.contractmanagement.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The expiry date on [whileTheServiceHasNoBoundedContexts].
 *
 * This service currently registers **no bounded contexts**. That is a deliberate, transient
 * state: the tour-booking example and the `mlc` context were deleted together, and the
 * `contract` context specified in `documentation/use-cases/uc01`–`uc06` has not been built
 * yet. While it lasts, sixteen architecture rules have no classes to check and are allowed to
 * pass empty.
 *
 * An allowance with no expiry is how a temporary exception becomes permanent — nobody
 * re-arms a gate they have forgotten is disarmed. So the allowance asserts its own
 * precondition, and this is it.
 *
 * **When this test fails, the service is no longer empty. Do not "fix" it — retire it:**
 *
 * 1. `grep -rn whileTheServiceHasNoBoundedContexts src/test` and delete every call.
 * 2. Delete `EmptyServiceAllowance.kt` and this file.
 * 3. Restore `isFailOnNoMatchingTests` on the `integrationTest` task in `build.gradle.kts`.
 *    It was disabled for the same reason — no `*IT` exists to match — and it is the one part
 *    of this arrangement that lives outside `src/test`, so it is the one most likely to be
 *    missed.
 * 4. Run `./gradlew check`. Any rule that now fails is a real violation in the new context,
 *    which is the whole point of the arrangement.
 * 5. Move `adr/0026-the-empty-service-is-a-transient-state.adr.md` to `Withdrawn` — its
 *    subject has stopped existing, which is what that status is for.
 *
 * Note that this test does **not** duplicate the registry. It reads
 * `architecture.definition.md` § 11 through [ContextRegistry], the same single source
 * `ContextRegistryTest` reads, so adding a row is all it takes to trip it. Registering a
 * context and creating its package must happen in the same increment anyway (§ 11), which
 * means this fires on exactly the commit that makes the rules enforceable again.
 *
 * SDD: see `documentation/adr/0026-the-empty-service-is-a-transient-state.adr.md`.
 */
class EmptyServiceTripwireTest {
    @Test
    @DisplayName("The empty-service allowance still has a reason — retire it when this fails")
    fun theServiceStillHasNoBoundedContexts() {
        assertThat(ContextRegistry.boundedContexts)
            .describedAs(
                "A bounded context is registered in architecture.definition.md § 11, so the " +
                    "architecture rules have a subject again and must stop being allowed to " +
                    "pass empty. Delete every call to whileTheServiceHasNoBoundedContexts, " +
                    "delete EmptyServiceAllowance.kt and this test, then re-run: whatever " +
                    "fails next is a real violation the allowance was hiding. Withdraw " +
                    "adr/0026 in the same increment.",
            ).isEmpty()
    }

    @Test
    @DisplayName("§ 11 still parses, and still registers the shared kernel and the composition root")
    fun theRegistryStillParses() {
        assertThat(ContextRegistry.registeredPackages)
            .describedAs(
                "the shared kernel and the composition root survive the emptying — they are " +
                    "not bounded contexts, so deleting every context does not remove them. " +
                    "This is the assertion ContextRegistryTest used to make about there being " +
                    "at least one context; it moved here because the two facts now have " +
                    "opposite lifetimes.",
            ).containsExactlyInAnyOrder("shared", "bootstrap")
    }
}
