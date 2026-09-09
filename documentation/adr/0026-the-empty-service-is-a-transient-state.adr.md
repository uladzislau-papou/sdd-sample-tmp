# ADR 0026 – The Empty Service Is a Transient State, and Its Gate Relaxation Expires On Its Own

## Status
Superseded by [0027](0027-a-rule-with-no-possible-subject-is-deleted-not-allowed-to-pass-empty.adr.md)

The retirement instruction below could not be executed: it assumed the first `contract`
increment would give every relaxed rule a subject, and five of them describe
`inbound.rest` and `inbound.listener` packages that `adr/0020` forbids the service from
having at all. 0027 splits the two populations. Superseded rather than withdrawn — the
subject, architecture rules matching nothing, still exists.

## Context

Deleting the `booking`, `guide` and `mlc` contexts in a single increment left this service
with **no bounded contexts at all**. The `contract` context decided in `adr/0024` is
specified in `documentation/use-cases/uc01`–`uc06` and not yet built.

That state broke the build in a way worth recording, because the failure was not a violation
of anything. `./gradlew test` reported **19 failures**, and they had four distinct causes:

| Cause | Count |
|-------|-------|
| ArchUnit rules reporting "failed to check any classes" | 16 |
| `ContextRegistryTest` asserting § 11 declares at least one bounded context | 1 |
| `TimestampRulesTest`'s `StartTourRequest` allowlist losing its subject | 2 |

The sixteen are the interesting ones. ArchUnit fails a rule whose input set is empty, and
that default is correct: a rule that silently matches nothing reports green while enforcing
nothing, which is the exact failure mode `adr/0014` exists to prevent. Rules describing
`..inbound.driver..`, `..core.inport.command..` and `..inbound.graphql..` have no subject in a
service with no contexts.

Three responses were available.

**Set `failOnEmptyShould=false` in an `archunit.properties`.** This works, takes one line, and
is the wrong answer: it disarms every rule in the build, invisibly, permanently, and for
reasons a future reader cannot reconstruct from the file.

**Delete the orphaned rules.** They would have to be rewritten from scratch on the day the
first context lands — which is precisely the day they are most needed and least likely to be
remembered.

**Say what is actually true**, which is narrower than either: *these rules have no subject
yet*. That is a fact about the service's current state, not about the rules.

## Decision

The sixteen rules are marked with a single named extension,
`ArchRule.whileTheServiceHasNoBoundedContexts()`, defined in `EmptyServiceAllowance.kt`. It
does nothing but call `allowEmptyShould(true)`, and exists so that the relaxation has **one
name, one place, and one grep target**.

The condition it is named after has an executable owner. `EmptyServiceTripwireTest` asserts
that `ContextRegistry.boundedContexts` is **empty**. It reads § 11 through the same parser
`ContextRegistryTest` uses, so it is not a second copy of the registry.

The moment a context is registered — which § 11 requires to happen in the same increment as
its package — that test fails, and its message is an instruction rather than a diagnosis:
delete every call to the extension, delete both files, re-run, and treat whatever fails next
as the real violation the allowance was covering. This ADR moves to `Withdrawn` in that same
increment.

One further relaxation belongs to the same decision and is recorded here so it is not
forgotten: the `integrationTest` Gradle task sets `isFailOnNoMatchingTests = false`, because
there is no `*IT` to match and Gradle fails a Test task whose filter selects nothing. It is
listed in the tripwire's retirement instructions as the one part of the arrangement living
outside `src/test`, and therefore the one most likely to be missed.

Two consequential edits came with it:

- `ContextRegistryTest`'s "§ 11 declares at least one bounded context" assertion was
  **inverted and moved** into the tripwire. It was never a property of the registry; it was a
  property of the service having been built. Two facts with opposite lifetimes should not
  share an assertion.
- `TimestampRulesTest`'s `StartTourRequest` allowlist was **deleted, not repaired**. Its
  companion test existed to fail when the exclusion lost its subject, it did exactly that,
  and its own instruction said to retire both. That is the precedent this ADR generalises.

## Rationale

**An exception with no expiry becomes permanent.** Not through negligence — through
invisibility. Nobody re-arms a gate they have forgotten is disarmed, and nothing in a green
build says "sixteen rules matched nothing today".

**The thing that rots is the workaround, so the workaround is what gets the executable
owner.** `adr/0014` says to give a rotting rule an enforcer rather than restating it more
firmly. Applied one level down: the relaxation is the claim most likely to outlive its
justification, so the relaxation is what asserts its own precondition.

**This pattern has already been proven in this repository, and its expiry has already been
observed.** The `StartTourRequest` exclusion was granted the same way, with a companion test
asserting the violation still existed. When the tour example was deleted, that test failed on
the next run and the exclusion was removed. The mechanism is not theoretical here; it has
completed one full cycle.

**Naming the extension after the condition, not the mechanism, is deliberate.** A call site
reading `.allowEmptyShould(true)` says what it does. One reading
`.whileTheServiceHasNoBoundedContexts()` says when it stops being true — and a reader who
notices the service does have a bounded context now knows the code is lying without needing
to find this ADR.

## Consequences

- Sixteen architecture rules in `ClassRoleRulesTest`, `DependencyRulesTest` and
  `TimestampRulesTest` currently pass without checking anything, and this is recorded rather
  than hidden. Anyone reading a green build should know it.
- `./gradlew check` is green on a service with no domain code, which makes the next
  session's first failing test a real signal instead of noise in an already-red build.
- The first increment of the `contract` context costs slightly more than it otherwise would:
  it must retire the allowance and fix whatever the re-armed rules then find. That cost is
  the point — it is paid at the moment there is something to check, by the person who added
  it.
- `whileTheServiceHasNoBoundedContexts` is a deliberately unmistakable identifier. If it
  appears in a commit after the `contract` context exists, the tripwire has been deleted
  rather than obeyed, and that is a review finding.
