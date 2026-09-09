# Tasks — UC01 CreateMaster

Driven by `/loop-uc uc01`. See `documentation/loop.playbook.md`.

## DoD Scoreboard – UC01 (iteration 3/10 — LOOP TERMINATED: BLOCKED)

Mirrored from `documentation/use-cases/uc01-create-master.spec.md` § 10.
Authoritative source is the spec; this table is rebuilt each iteration.

### Behaviour
- [ ] AC-01 covered by `CreateMasterDriverTest` — saved aggregate's status, empty contract list, `createdAt`
- [ ] AC-02 covered by `CreateMasterDriverTest` — one `MasterCreated` published
- [x] AC-03 covered by `MasterNameTest.blankNameThrows`, `MasterNameTest.whitespaceOnlyNameThrows`,
      `MasterNameTest.nameLongerThanTheLimitThrows`,
      `MasterNameTest.nameLongerThanTheLimitOnlyBeforeTrimmingThrows` — one case per rejection
      reason, plus the raw-vs-trimmed boundary
- [ ] AC-04 covered by `CustomerNumberTest` — one case per rejection reason
- [ ] AC-05 covered by `MasterJpaRepositoryIT` — two Masters with one customer number
- [ ] Domain invariants for `Master` covered by `MasterTest`
- [ ] Driver orchestration and event emission covered by `CreateMasterDriverTest`
- [ ] Every failure scenario in § 8 has a negative test
- [ ] Driver reads time from `ClockPort`, covered by `CreateMasterDriverTest` with a fixed clock

### Contracts
- [ ] `api/uc01-create-master.http` — not applicable, § 9 says REST is not
- [ ] `api/uc01-create-master.graphql` covers every classification in § 9
- [ ] Persistence roundtrip covered by `MasterJpaRepositoryIT`
- [ ] `documentation/ports/create-master.inport.spec.md` reflects the port as implemented
- [ ] `documentation/ports/master-repository.outport.spec.md` reflects the port as implemented

### Governance
- [x] `architecture.definition.md` § 11 has a `contract` row, landing with the package
      — verified by `ContextRegistryTest.topLevelPackagesMatchTheRegistry`
- [ ] `ContractSliceAllowance` / `ContractSliceTripwireTest` retired and `isFailOnNoMatchingTests`
      restored in `build.gradle.kts`, when `ContractSliceTripwireTest.theContractSliceIsStillMissingAtLeastOneLayer`
      fails, per `adr/0027`
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` on the architecture axis
- [ ] `conformance-reviewer` matches every § 7 criterion to a test and every § 10 box to an artifact
- [ ] Quality gates green (`test.definition.md` § 7)

**2 / 20 ticked — loop terminated BLOCKED at iteration 3.**

Unchanged this iteration. Iteration 3 added no production code — it closed G1–G4 (below)
through enforcement (`DeletedTransportRulesTripwireTest`) and documentation
(`coding-style.definition.md` § 9, `adr/0027`, rank-1 doc corrections). None of that touches
AC-01, AC-02, AC-04, AC-05, or any other unticked box, so nothing new is ticked; what ticks
next depends on iteration 3's `ddd-hex-reviewer` verdict (`loop.playbook.md` § 2.5).

## Open findings — carried into the next iteration

From `ddd-hex-reviewer`, iteration 1. These lead the queue (`loop.playbook.md` § 2.2).

- **F1 — RESOLVED (iteration 2).** ADR-0026's retirement instruction was unexecutable:
  five of the rules it relaxed describe `inbound.rest` / `inbound.listener` packages that
  `adr/0020` forbids the service from ever having, so its precondition could not expire.
  Halted and asked. Decision: delete the five rules, restore them with the transport that
  gives them a subject. `adr/0027` records it; `adr/0026` is superseded; the remaining
  allowance is renamed `whileTheContractSliceIsIncomplete` with `ContractSliceTripwireTest`
  keyed to the persistence adapter. Gates green: 32 tests, 0 failures.
- **F2 — RESOLVED (iteration 2).** `uc01-create-master.spec.md` § Bounded Context said
  `contract` "is **not yet** a row in `architecture.definition.md` § 11", which iteration 1
  had already made false. The paragraph now cites § 11 and `adr/0024` instead of restating
  the format contract.
- **F3 — RESOLVED (iteration 2).** The 200-character bound's subject is now documented and
  tested: `aggregate-master.spec.md` I-01 states that the bound measures the raw value, not
  the trimmed one, and `MasterNameTest.nameLongerThanTheLimitOnlyBeforeTrimmingThrows` pins
  the boundary (2 characters of padding + 199 = 201 raw, rejected). UC01 § 10's AC-03 citation
  and its `tasks.md` mirror both list the new method.

### From `ddd-hex-reviewer`, iteration 2. Closed in iteration 3.

- **G1 — RESOLVED (iteration 3).** `adr/0027`'s restoration instruction now has an executable
  owner: `DeletedTransportRulesTripwireTest.noAdapterExistsForTheRulesThatWereDeleted` fails
  the day any production class lands in `..inbound.rest..` or `..inbound.listener..`, and its
  failure message names the five rules to restore.
- **G2 — RESOLVED (iteration 3).** `coding-style.definition.md` § 9 now carries a table listing
  the five rules whose ArchUnit enforcer `adr/0027` deleted, each row naming the surviving
  specification and the deleted enforcer (`ClassName` · `methodName`, written with a middot
  rather than a period so `SpecCitationsTest` does not treat it as a live citation).
- **G3 — RESOLVED (iteration 3, code already correct).** `ContractSliceTripwireTest`'s method
  is renamed `theContractSliceIsStillMissingAtLeastOneLayer` and its trigger is the conjunction
  of `.inbound.driver`, `.core.inport`, `.core.outport` and `.inbound.graphql`, not the first
  class under `contract..outbound..` — so an out-of-order (e.g. persistence-first) increment no
  longer retires the allowance while GraphQL-keyed rules are still subjectless. UC01 § 10 and
  its `tasks.md` mirror cite the new method name.
- **G4 — RESOLVED (iteration 3).** `project.definition.md`, `technical.spec.md`, `CLAUDE.md`
  and `README.md` describe the post-`adr/0027` tree ("`contract` bounded context is registered
  and UC01 is part-built" / population A vs. population B), not the pre-`adr/0026` empty
  service. `HANDOFF.md` keeps the old account but under an explicit "Superseded in a later
  session — read this box first" callout, so it is a historical record rather than drift.

### New — from `ddd-hex-reviewer`, iteration 3. Unresolved; the loop stopped here.

All eight are documentation/enforcement rot exposed by registering the `contract` context.
None concerns UC01 behaviour.

1. **`build.gradle.kts:105-117`** — the `integrationTest` filter comment still says "remove
   with the empty-service allowance", "the service has no bounded contexts", "sixteen
   ArchUnit rules", and points at the deleted `EmptyServiceTripwireTest` and superseded
   `adr/0026`. This is the file `ContractSliceTripwireTest` singles out as "most likely to be
   missed", and its own pointer is now dangling in both directions.
2. **`SharedConfig.kt:23-25`** — production KDoc still asserts "the service currently has
   **no** bounded contexts" and cites `adr/0026`.
3. **`coding-style.definition.md:409`** — the new § 9 table miscites where the
   `@RestController` registration rule lives: it is `architecture.definition.md` § 4.5, not
   § 3.3. A rule-to-enforcer map that misplaces a rule is the one error it cannot afford.
   Related: the five rule names now exist in three unchecked copies (`adr/0027`, § 9's table,
   `DeletedTransportRulesTripwireTest`) and the `·` device means `SpecCitationsTest` will
   never catch a typo in any of them.
4. **`adr/0024:82-83`** — an Accepted, still-governing ADR cites the deleted
   `EmptyServiceTripwireTest`, the stale count "sixteen", and superseded `adr/0026`.
   `adr/README.md` records the ADR-0012 precedent for how to fix this legitimately.
5. **`notes.md:126-131`** — says `contract` is "not yet registered, on purpose" and repeats
   "sixteen architecture rules". Non-authoritative, but `file-usage.definition.md` § 2 says it
   may not *contradict* a formal definition.
6. **`schema.graphqls:6-7`** — "the `contract` context … has not been built yet" is now false.
7. **`ContractSliceTripwireTest:12-13`** — the KDoc claims more than the trigger delivers.
   Six of fifteen call sites key on something other than package population: two on the class
   name `*GraphQLController` (no package constraint at all), three on distinct `core.inport`
   subpackages that `contains(".core.inport")` cannot tell apart, one on "implements a
   `*UseCase` port". The `*GraphQLController` case is the live one. Same defect shape as G3,
   one level finer. Also: `DependencyRulesTest:49` and `:175` are already redundant — their
   subjects are populated today and the allowance can come off now.
8. **`project.definition.md`, `README.md`, `CLAUDE.md`** — my G4 fixes reintroduced the
   problem they fixed, in narrower form: all three now restate `adr/0027`'s falsifiable
   count ("five") and package list, which `coding-style.definition.md` § 9's table owns.
   Keep the orientation sentence, drop the enumeration, cite § 9.

**Also flagged, not a finding:** nothing is committed. `file-usage.definition.md` § 5.1
requires the `coding-style.definition.md` § 9 change to land in its own commit *before* the
commit deleting the five ArchUnit rules, because § 9 *loosens*. The
`architecture.definition.md` § 11 row is the documented exemption and lands with the package.
Committed as one blob, this becomes DRIFT.

## Deviations from `plan.md`

- **The `contract` § 11 row landed in an iteration that did not retire the empty-service
  allowance.** `execution.playbook.md` § 1 defines an increment as one inner-loop pass, so
  this departs from `adr/0026`'s "same increment" instruction. Recorded here rather than
  left implicit; F1 is the decision that resolves it.
