# DoD Scoreboard – UC07 (iteration 4/10)

Mirrored from `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 10.
**The spec is authoritative; this table is rebuilt each iteration** and is not a second source.

Nothing here is ticked on my own judgement. Every tick below rests on
`conformance-reviewer`'s iteration-3 **PASS**, which walked all 22 boxes and read the assertion
bodies rather than matching names. Per `loop.playbook.md` § 2.5 a `DRIFT` finding blocks every
box it touches, so the governance row for the architecture axis stays open — `ddd-hex-reviewer`
returned `DRIFT` three times and iteration 4 is in flight.

### Review history — the argument for not stopping at round two

| Round | `ddd-hex-reviewer` | `conformance-reviewer` |
|---|---|---|
| 1 | `DRIFT` — read path re-validated while two specs said it did not; § 5.1 commit ordering | `UNMET` — no `INTERNAL_ERROR` note in `api/`; § 8's classification untested |
| 2 | `DRIFT` — `ReconstitutionTest` expected `RuntimeException` at ten sites; three artifacts, three contracts for `DomainEventPublisher` | `UNMET` — § 8's rollback/no-dispatch half untested **and unowned** |
| 3 | `DRIFT` — the false "logs" claim in two more places; port spec's own Purpose contradicted its § 3; caller-side specs still attributed post-commit timing to the narrowed port | **`PASS`** — with one framing correction: the rollback deferral over-attributed UC07's own debt to the outbox increment |
| 4 | in flight | — |

Each round found what the previous fix had introduced. `HANDOFF.md` § 11 predicted exactly
this: *the temptation after round two is to declare it good enough.*

## Iteration 0 — Spec Phase (before the loop could start)

`/loop-uc` **refused** on its first invocation. The preflight's uncheckable-criterion gate
fired: eleven open questions (OQ 1–11) reached § 2, § 3, § 4, § 5, § 7, § 8 and § 9, and OQ 1
— which fields are mandatory — made the aggregate's constructor unspecifiable, so no RED test
could be written. `HANDOFF.md` § 7 said the same in words.

Closing them was a deliberate Spec Phase change, not a loop iteration, and it was done by
**our decision rather than by the project's answer**. Recorded as `PD-01 … PD-11` in the
spec's § 2, each stating that it is unsourced and what changes if the answer differs.

- [x] `BLOCKED` status added to `use-case.spec.template.md` + `/loop-uc` preflight gate
- [x] OQ 11 closed by an `inbound.graphql` row in `architecture.definition.md` § 8.1
- [x] Shared-kernel identity question closed by `adr/0023` — decided **against** promotion
- [x] `notes.md` records that the eleven are closed provisionally and still need real answers

## Behaviour

- [x] AC-01 covered by `CreateMasterLeasingContractDriverTest.create_savesAggregateWithExactlyOneCurrentConfiguration`
- [x] AC-02 covered by `CreateMasterLeasingContractDriverTest.create_savesTermsUnchanged`
- [x] AC-03 covered by `CreateMasterLeasingContractDriverTest.create_throwsInvalidMasterLeasingContractException_whenEmployerIdBlank`
- [x] AC-04 covered by `CreateMasterLeasingContractDriverTest.create_publishesExactlyOneMasterLeasingContractCreated`
- [x] AC-05 covered by `CreateMasterLeasingContractDriverTest.create_savesStatusActive`
- [x] AC-06 covered by `CreateMasterLeasingContractDriverTest.create_usesClockPort_forCreationTimeAndActivationDate`
- [x] AC-07 covered by `CreateMasterLeasingContractDriverTest.create_throwsInvalidMasterLeasingContractException_whenPriceRangeMinExceedsMax`
- [x] AC-08 covered by `CreateMasterLeasingContractDriverTest.create_succeeds_whenOptionalTermsAbsent`
- [x] Aggregate invariants — 4 `MasterLeasingContractTest` methods
- [x] Every § 2.3 value-object rule — 15 citations across 9 test classes
- [x] The two `Int` rules — 2 `ContractConfigurationTest` methods
- [x] Persistence roundtrip — 2 `MasterLeasingContractJpaRepositoryIT` methods
- [x] GraphQL mapping — 2 `MasterLeasingContractGraphQLControllerTest` methods

## Structure

- [x] `mlc` registered in § 11 **and** the package exists — parsed both ways, so both or neither
- [x] PD-11: § 8.1 timestamp table carries an `inbound.graphql` row with an executable owner
- [x] `adr/0023` decides the shared-Value-Object question reserved by `adr/0005`
- [x] OQ 12 closed by `adr/0022`; `adr/0002`'s status unchanged
- [x] `DomainEventPublisher`'s KDoc no longer claims after-commit delivery

## Contracts

- [x] `api/uc07-create-master-leasing-contract.graphql` covers every § 9 classification
- [x] No `api/uc07-*.http` exists
- [x] Domain spec in `documentation/domain/`
- [x] Port specs in `documentation/ports/` — inport triple and repository outport

## Governance

- [ ] `spec-documenter` reconciliation — **not yet dispatched**, deliberately: it edits
      `documentation/`, which `conformance-reviewer` is currently reading, and letting the
      files change under a reviewer would make its verdict a statement about a tree that no
      longer exists
- [ ] `ddd-hex-reviewer` — **running**
- [ ] `conformance-reviewer` — **running**
- [x] Quality gates green (`test.definition.md` § 7) — `./gradlew clean check` and
      `./gradlew build` both pass, **181 tests, 0 failures** (was 131). Item 12 of that list
      is the two reviewer axes and is therefore not yet met, which is why this box being
      ticked is about items 1–11 only

## Verification evidence

| Gate | Result |
|---|---|
| `./gradlew clean check` | BUILD SUCCESSFUL — test + integrationTest + spotlessCheck + detektMain + detektTest |
| `./gradlew build` | BUILD SUCCESSFUL |
| Test count | 181 total, 0 failures — 50 new |
| Integration tests | pass against real PostgreSQL; migration + JPA mappings agree under `ddl-auto: validate` |

## RED evidence (`tdd.definition.md` § 2)

Each layer's failure was quoted before its production code was written:

| Layer | RED |
|---|---|
| Documentation gates | `SpecCitationsTest > Every test citation in documentation/ resolves to a test that exists FAILED` — `AssertionError at SpecCitationsTest.kt:48` |
| Domain | `e: ContractConfigurationTest.kt:3:77 Unresolved reference 'exception'` and 13 more |
| `PriceRange.of` | `e: PriceRangeTest.kt:46:41 Unresolved reference 'of'` |
| Driver | `e: CreateMasterLeasingContractDriverTest.kt:44:26 Unresolved reference 'CreateMasterLeasingContractDriver'` |
| GraphQL | `e: MasterLeasingContractGraphQLControllerTest.kt:42:30 Unresolved reference 'MasterLeasingContractGraphQLController'` |
| Persistence | `e: MasterLeasingContractJpaRepositoryIT.kt:53:9 Unresolved reference 'MasterLeasingContractJpaRepository'` |
| § 8.1 rule | `TimestampRulesTest > § 8.1: no REST request or GraphQL input type carries a timestamp FAILED` — **7 violations, all pre-existing**, in `StartTourRequest` |

The last row is the one worth reading. The rule was written to enforce a *new* row in § 8.1
and it failed on its first run against **existing** code — `StartTourRequest.startedAt` is an
`Instant` on a REST request body, which is exactly the pattern § 8.1 was written to end. It
survived because § 8.1 had no executable owner until now.

## Open findings carried forward

1. **`StartTourRequest` violates § 8.1** — a document conflict, not a coding slip.
   `uc05-start-tour.spec.md` § 2 *specifies* `startedAt` as an optional request body field and
   § 3 gives it a 409; § 8.1 says "MUST NOT accept one from the request … Nothing needs
   validating, because nothing is accepted." `CLAUDE.md`'s authority order settles it —
   `architecture.definition.md` is rank 2, a use-case spec is rank 16 — so **UC05's spec is
   wrong and the code follows it**.
   Excluded from the new rule by a *named* allowlist of one, carrying its reason, and guarded
   by `TimestampRulesTest.theKnownViolationStillExists` so the exclusion cannot outlive its
   violation. Fixing it properly removes a documented feature, its exception path, three
   `GuideTourRestControllerTest` cases and two requests in `api/uc05-start-tour.http` — an
   increment of its own, with a spec change, and outside what this use case was asked to do.
   **Owner: a UC05 increment. Flagged to the user rather than decided.**
2. **`SpecCitationsTest` keys declared test methods by *file* name, not class name.** Nested
   test classes are therefore invisible to it, and a citation naming a nested class would
   silently resolve to nothing. Found by writing the value-object tests as nested classes
   first; worked around by giving each value object its own file, which is the repository's
   existing convention anyway. Not fixed — the parser change is its own increment.

## Next step

Read both reviewer verdicts. Tick only what they confirm, dispatch `spec-documenter` after
they return, then re-evaluate § 10 and check the three exit conditions.
