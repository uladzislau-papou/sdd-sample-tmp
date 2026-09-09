# ADR 0027 – A Rule With No Possible Subject Is Deleted; One With No Subject *Yet* Keeps an Expiring Allowance

## Status
Accepted

Supersedes [0026](0026-the-empty-service-is-a-transient-state.adr.md).

## Context

`adr/0026` allowed sixteen ArchUnit rules to match nothing while the service had no bounded
contexts, behind one named extension — `whileTheServiceHasNoBoundedContexts()` — whose
precondition `EmptyServiceTripwireTest` asserted. Its retirement instruction was
unconditional:

> The moment a context is registered … that test fails, and its message is an instruction
> rather than a diagnosis: delete every call to the extension, delete both files, re-run, and
> treat whatever fails next as the real violation the allowance was covering. This ADR moves
> to `Withdrawn` in that same increment.

The first increment of the `contract` context registered the `§ 11` row and fired the
tripwire exactly as designed. The instruction could not be followed, and the reason is not
that the increment was too small.

**The instruction was executed and measured.** With every `whileTheServiceHasNoBoundedContexts()`
call removed, `./gradlew test` reported **16 failures, 14 of them `failed to check any
classes`**. None was a violation to fix. The tree was restored and the measurement kept.

What the measurement showed is that 0026 treated one population where there are two:

| | Rules | Condition | Ends when |
|---|---|---|---|
| **A — no subject *yet*** | driver, `core.inport` triple, `core.outport`, `inbound.graphql` | UC01's vertical slice is unbuilt | the slice lands, inside UC01 |
| **B — no *possible* subject** | `..inbound.rest..` (4) and `..inbound.listener..` (1) | `adr/0020` makes GraphQL the only transport, and no use case in `uc01`–`uc06` is listener-driven | a REST endpoint or a listener is specified — no current spec asks for either |

0026 assumed the first `contract` increment would give every rule a subject. For population A
that is true a few iterations later than it supposed. For population B it is **never** true:
those five rules describe packages `adr/0020` forbids the service from having. An allowance
whose precondition cannot expire is precisely the permanent, invisible relaxation 0026 was
written to prevent — it had reproduced the defect it was diagnosing, one level down.

## Decision

**Population B is deleted.** The five rules with no possible subject are removed from
`ClassRoleRulesTest` and `DependencyRulesTest`:

- `restAdapterCarriesNoHttpAnnotation`
- `deliveryDtosCarryNoDomainType`
- `listenersDoNotTouchOutboundAdapters`
- `rule3_restDependsOnInportOnly`
- the `*RestController` half of `deliveryAdaptersAreRegisteredWithTheirFramework`

**They are re-added in the same increment as the transport they govern, and that
instruction has an executable owner.** `adr/0020` already says REST arrives with the first
machine consumer; this ADR adds that the increment adding a `*RestController` also restores
the four REST rules, and the increment adding an `inbound.listener` restores the fifth.
`coding-style.definition.md` § 3.3 and `architecture.definition.md` § 4.5 and § 4.8 remain the
specifications those rules enforce, so what is deleted is an enforcement with nothing to
enforce, not a rule.

`DeletedTransportRulesTripwireTest` asserts that no production class resides in
`..inbound.rest..` or `..inbound.listener..`, and names the five rules to restore in its
failure message. Without it this ADR would carry a restoration instruction in prose — which is
0026's defect one population over: 0026 had a *retirement* precondition that could not fire,
and this would have had a *restoration* precondition that did not exist. An ADR arguing that
prose rots may not rely on prose.

Rules whose *subject* is `inbound.graphql` but which *mention* `inbound.rest` as a target are
kept: `rule3c_transportsDoNotDependOnEachOther` and
`TimestampRulesTest.noTimestampFieldOnExternalTransportInputTypes` both acquire a subject as
soon as the GraphQL adapter exists, and both are keyed to more than one transport on purpose.

**Population A keeps an allowance, renamed to the condition that actually holds.**
`whileTheServiceHasNoBoundedContexts()` becomes `whileTheContractSliceIsIncomplete()`, and
`EmptyServiceTripwireTest` is replaced by `ContractSliceTripwireTest`, which asserts that at
least one of the four packages the relaxed rules are waiting on — `inbound.driver`,
`core.inport`, `core.outport`, `inbound.graphql` — is still empty. When all four are populated
the test fails, and the allowance is retired in that increment.

**The trigger is that conjunction and not "the last layer", deliberately.** This ADR first
keyed it to the first class under `contract..outbound..`, reasoning that persistence is last
in `tdd.definition.md` § 3's inside-out ordering so everything else would already exist. § 3
*permits justified deviation* from that ordering, and a persistence-first increment would then
fire the tripwire with five GraphQL-keyed rules still subjectless. That is precisely 0026's
failure — a precondition true of the intended path rather than of every path — and catching it
inside the ADR written to correct 0026 is the reason it is recorded here rather than quietly
fixed. Naming the four packages removes the dependency on ordering. `contract..outbound..` is
not among them: no relaxed rule waits on it, because
`DependencyRulesTest.rule5_outboundIsReferencedOnlyByBootstrap` already has `shared.outbound`
as its subject and carries no allowance.

**`adr/0026` moves to `Superseded by 0027`**, not `Withdrawn`: its subject — architecture
rules matching nothing — still exists. UC01 § 10's governance box is read accordingly.

The `integrationTest` task's `isFailOnNoMatchingTests = false` stays until the first `*IT`
exists, which is its own box in UC01 § 10 and is unchanged by this ADR.

## Rationale

**A rule is deleted when its subject cannot exist, and kept when its subject is merely
absent.** That is the distinction 0026 lacked, and it is the whole of this decision. Deleting
population A would throw away enforcement the next few iterations need. Allowing population B
to pass empty would install a relaxation with no expiry, which is what 0026 correctly refused
to do for a *transient* condition and then accidentally did for a permanent one.

**Deleting population B costs less than it appears to.** The rules are five short, declarative
ArchUnit statements whose specifications survive in
`architecture.definition.md` § 4.5 / § 4.8 and `coding-style.definition.md` § 3.3. 0026 argued
that deleted rules "would have to be rewritten from scratch on the day the first context lands
— which is precisely the day they are most needed and least likely to be remembered." That
argument is sound for population A and does not transfer to population B: the day a
`*RestController` first appears is a day someone is reading `coding-style.definition.md` § 3.3
to learn what to name it, and this ADR is cited from the deletion site.

**Naming the allowance after its condition is kept from 0026**, because that part worked. A
reader who sees `whileTheContractSliceIsIncomplete()` next to a finished slice knows the code
is lying without having to find this file.

**The pattern's failure mode is now recorded, not just its success.** 0026 cited the
`StartTourRequest` allowlist as a completed cycle proving the mechanism. The mechanism is
sound; what 0026 got wrong was the *scope* of the condition it named. An expiring allowance is
only as good as the honesty of its precondition, and "the service has no bounded contexts" was
a precondition that bundled two different futures together.

## Consequences

- Five architecture rules no longer exist. Between now and the first REST endpoint or
  listener, nothing checks that a `*RestController` carries no HTTP annotation, that a REST DTO
  carries no domain type, that `inbound.rest` depends only on `core.inport`, or that a listener
  avoids outbound adapters. **Nothing can violate them either**, since the packages do not
  exist — but a hand-written `inbound.rest` package added without restoring the rules would
  be unguarded for as long as it took to notice — which is now bounded by
  `DeletedTransportRulesTripwireTest` failing on the commit that creates the package.
  `ContextRegistryTest` does **not** cover this: it compares only the top-level segment, so
  `contract.inbound.rest` sits inside the registered `contract` package and passes.
- `whileTheContractSliceIsIncomplete` is the single grep target while UC01 is in flight. If it
  appears in a commit after all four awaited packages exist, the tripwire was deleted rather
  than obeyed, and that is a review finding — the same terms 0026 set.
- `coding-style.definition.md` § 9 lists the five deleted rules in its "no executable owner"
  row, because § 9 is the canonical rule-to-enforcer map (`adr/0014`) and it would otherwise
  name an enforcer that no longer exists.
- The retirement of the remaining allowance is now an executable instruction rather than an
  aspirational one, which is the defect this ADR exists to correct.
- `adr/0026`'s core argument — say what is actually true, and give the workaround an
  executable owner — is unchanged and is inherited wholesale. Only its scoping was wrong.
