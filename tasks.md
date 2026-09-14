## DoD Scoreboard – UC01 (iteration 15 — ddd-hex-reviewer PASS)

Mirrored from documentation/use-cases/uc01-register-master-leasing-contract.spec.md § 10.
Authoritative source is the spec; this table is rebuilt each iteration.

Note on `dod_history` below: iterations 1-10 counted this list as 17 items;
re-reading § 10 against the actual template finds 19 checkboxes (10
Behaviour + 6 Contracts + 3 Governance). The discrepancy predates iteration 11
and is carried forward rather than silently corrected.

### Behaviour
- [x] AC-01 covered by `MasterLeasingContractTest.register_createsContractInDraft`
      and `RegisterMasterLeasingContractDriverTest.register_savesAndPublishes`
- [x] AC-02 covered by `MasterLeasingContractTest.register_storesParentReference`
- [x] AC-03 covered by `MasterLeasingContractTest.register_throwsInvalidMasterLeasingContractException_whenParentIsSelf`
- [x] AC-04 covered by `PriceRangeTest.throwsInvalidMasterLeasingContractException_whenMinExceedsMax`
      and `RegisterMasterLeasingContractGraphQLControllerTest.register_returnsBadRequest_whenPriceBandInverted`
- [x] AC-05 covered by `PriceRangeTest.throwsInvalidMasterLeasingContractException_whenCurrenciesDiffer`
      (corrected iteration 12 — was `IllegalArgumentException`, a confirmed defect;
      further corrected iteration 14, see below — the "Given" scenario itself was
      unreachable through the command, since one `currency` field applies to every
      amount; AC-05 now describes PriceRange's own defensive VO guard)
- [x] AC-06 covered by `NoticePeriodTest` (boundary cases at 0, 1, 36, 37)
- [x] AC-07 covered by `MasterLeasingContractTest.register_throwsInvalidMasterLeasingContractException_whenInitialVersionIsNotOne`
      and `RegisterMasterLeasingContractGraphQLControllerTest.schema_exposesNoVersionField`
- [x] Domain invariants for `MasterLeasingContract` covered by `MasterLeasingContractTest`
- [x] Driver orchestration and event emission covered by `RegisterMasterLeasingContractDriverTest`
- [x] Every failure scenario in § 8 has a negative test — now genuinely exhaustive
      over every VO in the configuration, including the currency-ISO-4217 row added
      iteration 14 (`RegisterMasterLeasingContractDriverTest.register_throwsInvalidMasterLeasingContractException_whenCurrencyIsNotIso4217`,
      corrected during this pass — the spec's citation list had omitted it; see
      spec § 10 for the full test list)

### Contracts
- [x] `src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls` declares `registerMasterLeasingContract`
- [x] `graphql/uc01-register-master-leasing-contract.graphql` covers success and `BAD_REQUEST`
- [ ] Persistence roundtrip covered by `MasterLeasingContractPersistenceAdapterIT`,
      including that every monetary column round-trips at scale 4
- [ ] Flyway `V1__DDL_create_master_leasing_contract.sql` exists
- [x] `documentation/ports/master-leasing-contract-repository.outport.spec.md` reflects `save`
- [x] `documentation/ports/register-master-leasing-contract.inport.spec.md` reflects the inport

### Governance
- [x] This spec reconciled against the code by `spec-documenter`
- [x] `ddd-hex-reviewer` returns `PASS` — reached on the seventh pass (iteration
      15), after six rounds of DRIFT across iterations 11-14 (7 findings, then 3,
      then 4+1 governance, then 5, then 4 — each fixed and mutation-verified where
      applicable). Reviewer's closing words: "From an architectural-review
      standpoint this increment is done." Two non-blocking nitpicks noted (one
      fixed — a stray "I-14" in KDoc; one is commit-hygiene guidance, see below)
- [ ] Quality gates green (`test.definition.md` § 7) — ArchUnit suite exists and
      passes (`ContextRegistryTest` 7, `DependencyRulesTest` 8, `ClassRoleRulesTest`
      9 = 24 architectural of 54 total); `ddd-hex-reviewer` PASS achieved above;
      remains unticked only because the persistence gates (roundtrip IT, Flyway
      migration) are still unbuilt — `test.definition.md` § 7 is all-or-nothing

### Governance note — ADR question on the architecture.definition.md edits (iteration 12)

`ddd-hex-reviewer` flagged (iteration 13 pass 1, finding 5) that iteration 12's
edits to `architecture.definition.md` (§ 6 rule 3's core.domain-enum clause, § 4.5's
`Query` placeholder waiver) touch "architectural layering or dependency rules"
(`sdd.playbook.md` § 6 item 1), and asked whether they need an ADR.

Judgement call, made explicitly rather than silently: **no ADR**, for two reasons
specific to each edit —

1. **§ 6 rule 3's enum clause.** `ddd-hex-reviewer`'s pass 2 caught that my first
   justification for this was circular — I cited § 4.2 as already permitting
   commands to reference domain enums, when at the time § 4.2 said no such thing
   (the quoted sentence actually came from `register-master-leasing-contract.inport.spec.md`
   § 2.1, which in turn cited § 4.2 for a position § 4.2 didn't yet state). Fixed by
   adding the rule to § 4.2 itself, where it belongs (a positive statement about
   what `inport.command`/`inport.result` may reference), so § 6 rule 3 now cites a
   real sentence. The reviewer's own precedent applies here — this repository has
   prior art for landing a rule-loosening as a doctrine-only commit with its
   reasoning in the message (`file-usage.definition.md` § 5.1) rather than an ADR,
   for a change the reviewer judged substantially larger than this one (§ 11 rule 3,
   commit `3a414de`).
2. **§ 4.5's `Query` placeholder waiver** accommodates a hard external constraint —
   GraphQL requires exactly one `Query` root type with at least one field, a rule
   of the GraphQL specification itself, not a choice this project makes. Reviewer
   agreed this is not a trigger (§ 6 items 1, 8, 12 all miss).

**Commit-ordering obligation, not yet actionable:** nothing in this session is
committed. When it is, `architecture.definition.md` (§ 4.2, § 4.5, § 6 rule 3, § 9,
§ 11) MUST land in its own commit, strictly before any commit containing
`src/main/kotlin/.../inbound/graphql/**` — per `file-usage.definition.md` § 5.1,
committing them together is drift regardless of the reasoning above being sound.

If the user disagrees with the no-ADR reasoning, the edits are isolated to
`architecture.definition.md` § 4.2, § 4.5 and § 6 rule 3 and can be reverted to a
pre-edit state pending a formal ADR without touching any other file.

dod_history: [0/17, 1/17, 2/17, 3/17, 4/17, 5/17, 5/17, 6/17, 8/17, 8/17, 10/17, 15/19, 17/19, 17/19, 17/19, 16/19]
open_findings: []

Note on the final count (corrected during this reconciliation pass): the
previous entry read `18/19`. Recounting the 19 checkboxes above against their
actual `[x]`/`[ ]` state gives 10/10 Behaviour + 4/6 Contracts + 2/3 Governance
= **16/19**, not 18 — the two open Contracts boxes (persistence roundtrip,
Flyway migration) and the one open Governance box (quality gates green) were
being undercounted as closed. Fixed here; no checkbox state itself changed.

## Iteration Log

### Iteration 15 — ddd-hex-reviewer PASS (seventh pass)

Sixth `ddd-hex-reviewer` pass (verifying iteration 14's 5 fixes) returned DRIFT —
4 findings: (1) the currency-exception fix from round 4 had used the wrong
exception type (`IllegalArgumentException` instead of the domain exception —
`coding-style.definition.md` § 6.2 enumerates exactly two sanctioned IAE
backstops and a command-sourced ISO-4217 string is neither); (2) the exception
resolver's `IllegalArgumentException → BAD_REQUEST` mapping had no GraphQL-level
test, so deleting it would silently change three documented failure paths with
the suite staying green; (3)-(4) KDoc gaps on nullable properties and port/driver
functions (`coding-style.definition.md` § 1.4 / § 7.1), which the reviewer said
would not alone have justified a round.

All 4 fixed: (1) driver now catches `Currency.getInstance`'s IAE and rethrows
`InvalidMasterLeasingContractException` with the original as `cause` (added an
optional `cause` param to the exception class); genuine RED → GREEN, mirrored
into both specs. (2) added a GraphQL classification test, mutation-verified
(removed the resolver's IAE arm, caught, reverted, diff-confirmed 0 lines
changed). (3)-(4) KDoc added throughout — comment-only.

Seventh `ddd-hex-reviewer` pass (verifying all 4): **PASS.** Full checklist
walked once more, all 7 groups clean. Closing assessment quoted verbatim:
"From an architectural-review standpoint this increment is done." Two
non-blocking nitpicks: a stray "I-14" in one KDoc `@throws` line (should read
I-09 to I-13, since `register` cannot reach I-14/`CancellationReason`) — fixed
immediately, cost one character; and a commit-hygiene reminder that nothing is
committed yet, so `file-usage.definition.md` § 5.1's doctrine-first-commit
obligation is not yet violated but will be if `architecture.definition.md`'s
loosening edits land in the same commit as `src/**` — already recorded above
under "Governance note" and restated here for visibility at closeout.

`./gradlew clean test` and `./gradlew build` green (54 tests, 24 architectural,
0 skipped, 0 failures) after the final nitpick fix.

### Iteration 14 — DRIFT round-trip 3 (fourth and fifth reviewer passes)

Fourth `ddd-hex-reviewer` pass (verifying iteration 13's 4 ArchUnit gap fixes)
returned DRIFT — 5 findings:

1. `graphQlMappingAnnotations_onlyInInboundGraphql`'s split still missed
   `@SchemaMapping`'s class-level placement (`@Target({TYPE, METHOD})`, not
   `METHOD`-only like the other two). Fixed: added `classLevelGraphQlAnnotations_onlyInInboundGraphql`
   alongside the method-level rule.
2. Named-but-missing ArchUnit rules: four other port specs (UC04/UC08/UC05/UC09
   inport specs, and the aggregate spec's `reconstitute` note) cite ArchUnit test
   names verbatim as "their enforcement" that didn't exist —
   `masterLeasingReachesIndividualLeasingInport_onlyFromADriver`,
   `individualLeasingReachesMasterLeasingInport_onlyFromADriver`,
   `individualLeasing_doesNotImportMasterLeasingInternals`,
   `reconstitute_isCalledOnlyByPersistenceMappers`. Added all four to
   `ContextRegistryTest` (three `allowEmptyShould(true)`, since `individualleasing`
   doesn't exist yet; `reconstitute`'s matches by method name via a
   `DescribedPredicate<JavaMethodCall>`, so it's non-vacuous without the method
   existing). Mutation-verified `reconstitute_isCalledOnlyByPersistenceMappers`
   (added a live `reconstitute()` call from the driver, caught, reverted,
   diff-confirmed 0 lines changed).
3. AC-05's ticked test doesn't test AC-05's scenario — `PriceRangeTest`'s
   currency-mismatch test constructs `PriceRange` with mismatched currencies
   *within the band*, but AC-05 says "credit limit EUR, price band CHF" (a
   cross-field scenario `PriceRange` never sees). Investigated: the inport spec's
   `currency` field ("applies to every amount below") makes AC-05's original
   scenario structurally unreachable through the command — a pre-existing
   inconsistency in the use-case spec, not introduced this session. Fixed by
   rewriting AC-05 and its § 2 validation-rules bullet to describe what's
   actually tested (`PriceRange`'s defensive VO guard) with an explicit
   unreachability note, rather than inventing an unneeded cross-field invariant.
4. Circular citation: my iteration-12 fix for § 6 rule 3's core.domain-enum
   clause cited § 4.2 as already permitting inport commands to reference domain
   enums — § 4.2 said no such thing at the time; the quoted sentence actually
   came from the inport spec, which itself cited § 4.2 for a position § 4.2
   didn't state. Fixed properly this time: added the permission to § 4.2 itself
   (a real, positive rule), so § 6 rule 3 now cites a sentence that exists.
   Extended the same clause to cover `*Input`/`*Payload` field types (closing
   the `MlcConfigurationInput.kt` gap flagged as "undocumented" two rounds
   earlier). Corrected `tasks.md`'s governance note accordingly and added the
   explicit commit-ordering obligation the reviewer named (citing repo precedent
   commit `3a414de` for doctrine-only rule loosening without an ADR).
5. Unspecified currency-parsing failure (`Currency.getInstance` can throw
   `IllegalArgumentException` for a malformed ISO-4217 code, undocumented,
   untested). Added a § 8 row and a driver test, mutation-verified.

Fifth `ddd-hex-reviewer` pass (verifying all 5) returned DRIFT again — 4 findings,
of which the reviewer explicitly said 2 (KDoc gaps) would not alone have
justified a round, and 2 were real:

1. **Real defect, introduced by round 4's own fix.** Round 4 fixed the *test
   name* for the currency failure but left the exception type as
   `IllegalArgumentException` — and `coding-style.definition.md` § 6.2 enumerates
   the IAE-backstop as "exactly two things" (shared-kernel VOs, and
   `UUID.fromString` on a GraphQL `ID`), neither of which a command-sourced
   ISO-4217 string is. Fixed: `RegisterMasterLeasingContractDriver` now catches
   `Currency.getInstance`'s `IllegalArgumentException` and rethrows
   `InvalidMasterLeasingContractException` (with the original as `cause` — added
   an optional `cause` parameter to the exception class). Genuine RED → GREEN
   (test renamed and retyped, confirmed RED against the old code, then fixed).
   Mirrored into `register-master-leasing-contract.inport.spec.md` § 2.3 and
   `uc01-...spec.md` § 8.
2. **Real hole**: the exception resolver's `IllegalArgumentException` →
   `BAD_REQUEST` mapping had no GraphQL-level test — deleting that arm of the
   `when` would silently change three documented failure paths from
   `BAD_REQUEST` to `INTERNAL_ERROR` with the suite staying green. Added
   `RegisterMasterLeasingContractGraphQLControllerTest.register_returnsBadRequest_whenIllegalArgumentExceptionThrown`,
   passed unexpectedly (existing resolver behaviour), mutation-verified (removed
   the `is IllegalArgumentException` arm, confirmed caught, reverted,
   diff-confirmed 0 lines changed).
3. KDoc gaps on nullable properties (`parentMasterLeasingContractId`,
   `activationDate`, `cancelledDate`, `cancellationReason` on the aggregate; same
   field on the command and input types) and on port/driver function signatures
   (`coding-style.definition.md` § 1.4 / § 7.1). Added throughout — comment-only,
   no behaviour change.
4. `register-master-leasing-contract.inport.spec.md` § 2.3 didn't yet mirror the
   currency-exception decision from finding 1 above — same commit closes both.

Reviewer's own words on reaching this point: "I do not expect a seventh round to
find anything of this size — the structural work is sound... groups 1, 2, 5 and 6
came back clean on a full re-walk." A seventh pass is queued purely to confirm,
not because specific defects are expected.

`./gradlew clean test` and `./gradlew build` green throughout iteration 14 (54
tests total, 24 architectural).

### Iteration 13 — ArchUnit suite (ADR 0007's unmet obligation)
Third `ddd-hex-reviewer` pass (verifying iteration 12's I-10/I-11/I-13 fixes)
returned DRIFT with exactly one finding: no ArchUnit suite exists, an Accepted-ADR
(0007) obligation, blocking "Quality gates green" for any use case, not just UC01.

Built `src/test/kotlin/com/jobradleasing/contractmanagement/architecture/`:
`ContextRegistryTest`, `DependencyRulesTest`, `ClassRoleRulesTest` — names already
referenced by `architecture.definition.md` § 11, `coding-style.definition.md` § 5.1,
`modelling.definition.md`, and `clock.outport.spec.md` § 5, so kept consistent.
All use `ClassFileImporter().withImportOption(DO_NOT_INCLUDE_TESTS)` — necessary
because test classes share package names with production classes in this codebase,
so without it every rule would flag test-only imports (AssertJ, JUnit) as
violations. Had to allow `org.jetbrains.annotations..` in two `onlyDependOnClassesThat`
rules — Kotlin emits `@NotNull`/`@Nullable` from that package into bytecode for
every non-null/nullable declaration; verified via `javap -v` this is a JVM-interop
artifact with no runtime behaviour, not a real dependency.

Proved the suite isn't vacuous with two mutations (each reverted, confirmed
byte-identical via `diff`): pointed the base package at a nonexistent package
(caught by `importer_findsProductionClasses`); added a live `org.springframework.util.Assert`
call to a `core.domain` class (caught by `coreDomain_dependsOnNothingElse`).

Fourth `ddd-hex-reviewer` pass (verifying the suite) returned DRIFT again — the
suite ran green but 4 of its checks didn't actually check what they claimed:

1. `graphQlMappingAnnotations_onlyInInboundGraphql` predicated on classes carrying
   `@MutationMapping`/`@QueryMapping` — both are `@Target(METHOD)` only, never
   applicable to a class, so those two clauses were unsatisfiable by construction
   and the rule passed vacuously. **This one would have let a `@MutationMapping`
   method sit inside `inbound.driver` and stayed green.** Fixed: split into a
   method-level rule (`graphQlMappingMethods_onlyInInboundGraphql`, using
   `methods()` not `classes()`) plus a separate class-level `@Controller` check.
   Mutation-verified (added a live `@MutationMapping` method to the driver,
   caught, reverted).
2. No test named `shared_dependsOnNoBoundedContext` existed, despite
   `coding-style.definition.md` § 6.2 naming it verbatim as the enforcement behind
   the shared-kernel exemption. Added to `ContextRegistryTest`. Mutation-verified
   (added a live reference from a `shared.domain` class to a `masterleasing` type,
   caught, reverted).
3. The framework-free check covered `core.inport`/`core.outport` but not
   `core.domain`, `shared.domain` or `shared.outport` — despite § 9 stating the
   framework-free constraint applies to the latter two "exactly as it applies to a
   context's `core`". `ClockPort`/`DomainEventPublisher` were unguarded. Widened.
4. No check for `java.util.Optional` in `core`/`shared`, despite
   `master-leasing-contract-repository.outport.spec.md` § 1 naming this exact
   enforcement. Added.

Fifth pass (verifying these 4 fixes) — pending, see next iteration.

Also raised (finding 5, not an ArchUnit gap): whether iteration 12's
`architecture.definition.md` edits needed an ADR. Judgement call recorded above
under "Governance note" — no ADR, with reasoning; both edits are isolated and
revertable if the user disagrees.

`./gradlew clean test` and `./gradlew build` green throughout (now 45 tests total,
21 of them architectural).

### Iteration 12 — DRIFT resolution round 2 (post-review fixes)
Real `ddd-hex-reviewer` and `spec-documenter` became available this session
(org spend limit reset). Iteration 11 dispatched both for the first time on the
full accumulated diff (iterations 1-11). `spec-documenter` reconciled cleanly.
`ddd-hex-reviewer` returned **DRIFT** — 7 findings, most seriously: `PriceRange`'s
currency-mismatch check threw `IllegalArgumentException`, but the domain spec's
I-09 (higher authority than the use-case spec) requires
`InvalidMasterLeasingContractException` since `PriceRange` is a context-owned VO
with its own domain exception available (`coding-style.definition.md` § 6.2). This
touched the already-ticked AC-05.

Fixed, each via its own RED → GREEN:
- `PriceRange` currency-mismatch now throws `InvalidMasterLeasingContractException`
  (was `IllegalArgumentException`) — genuine behaviour change, driven by a fresh RED
  against the renamed test `PriceRangeTest.throwsInvalidMasterLeasingContractException_whenCurrenciesDiffer`.
- `PriceRange` now also enforces non-negative amounts (I-09's second clause, was
  entirely unenforced) — `throwsInvalidMasterLeasingContractException_whenMinIsNegative`.
- `EmployerId`/`LessorId` (shared.domain) now guard blank values —
  `EmployerIdTest`, `LessorIdTest` — `IllegalArgumentException` per the
  shared-kernel exemption.
- `RegisterMasterLeasingContractDriverTest.register_throwsIllegalArgumentException_whenParentIdIsMalformed`
  now also asserts `repository.saved`/`publisher.published` stay empty — passed
  unexpectedly (existing behaviour), proved non-vacuous by a temporary mutation
  (inserted a premature save+publish, confirmed the new assertions caught it,
  reverted).
- `architecture.definition.md`: sanctioned the placeholder `Query` type (§ 4.5),
  documented the `core.domain` enum exception on `inbound.graphql` (§ 6 rule 3),
  and added `Percentage` to the `shared` enumeration (§ 9, § 11).
- `register-master-leasing-contract.inport.spec.md` § 3: documented the
  `@Service`/`@Transactional` deferral explicitly (no persistence adapter exists
  yet to make the bean meaningful) — reviewer confirmed this is an acceptable,
  tracked deferral, not drift.
- `notes.md`: recorded the `MlcConfiguration`-has-no-identity tension the reviewer
  flagged (a modelling question, not a quick fix) — reviewer confirmed recording it
  is acceptable, decide before UC03.

Re-dispatched the *same* reviewer agent (continuing its context, not a fresh roll)
to verify the fixes — this returned **DRIFT again**, 3 further findings: I-10
(`CreditLimit`), I-11 (`EligibleEmployees`) and I-13 (`ReturnQuota`) were declared
in the spec and enforced nowhere in code (same class of defect as `PriceRange`,
in the VOs not touched in round 1); the "every failure scenario" DoD box was
ticked despite its own parenthetical admitting incompleteness; and the new
`Percentage` § 9 argument claimed a "bounds" invariant `Percentage` doesn't
enforce.

Fixed:
- Added RED-driven guards to `CreditLimit` (I-10), `EligibleEmployees` (I-11),
  `ReturnQuota` (I-13 — the `0..100` bound placed on `ReturnQuota` itself per the
  reviewer's read of the spec wording, not on `Percentage`), and — while in the
  neighbourhood, since they're the same defect class and cheap — `ConfigurationVersion`
  (`>= 1`, stated in the aggregate spec § 2a) and `CancellationReason` (I-14,
  non-blank + 400-char max). New test classes: `CreditLimitTest`,
  `EligibleEmployeesTest`, `ReturnQuotaTest`, `ConfigurationVersionTest`,
  `CancellationReasonTest`.
- Reverted the "every failure scenario" tick, then re-ticked once genuinely
  exhaustive (all of I-01 through I-14 now enforced and tested).
- Fixed the `Percentage` § 9 argument to drop the "bounds" claim — `Percentage`
  enforces scale only; the bound is `ReturnQuota`'s.
- `aggregate-master-leasing-contract.spec.md` § 7: ticked "Value object invariants
  I-09 to I-14" — now exhaustive.

A third `ddd-hex-reviewer` pass is queued (not yet run) before "returns PASS"
ticks. `./gradlew clean test` and `./gradlew build` both green throughout.
Reviewer also flagged (informational, not blocking): `architecture.definition.md`
doc changes should land in their own commit before the `src/**` commit, per
`file-usage.definition.md` § 5.1 — relevant when this is actually committed.

### Iteration 11 — GraphQL layer for UC01
Built, for the first time: the schema (`master-leasing-contract.graphqls`,
including a placeholder `Query` root type — GraphQL requires one and no
read-side use case exists yet), `RegisterMasterLeasingContractInput`/
`MlcConfigurationInput`, `RegisterMasterLeasingContractPayload`,
`RegisterMasterLeasingContractGraphQLController`,
`MasterLeasingContractExceptionResolver`, and
`graphql/uc01-register-master-leasing-contract.graphql`. Three passing tests in
`RegisterMasterLeasingContractGraphQLControllerTest` (success, `BAD_REQUEST` on
inverted price band, schema introspection proving no `version` field).

Notable implementation wrinkle: Spring GraphQL's `DataFetcherExceptionResolverAdapter`
+ `.errorType(ErrorType.BAD_REQUEST)` alone does NOT populate
`extensions.classification` for `@GraphQlTest`'s in-process `GraphQlTester` — that
derivation only happens via `GraphQLError.toSpecification()`, used for real
HTTP/JSON serialization, which `@GraphQlTest` bypasses. Fixed by setting
`.extensions(mapOf("classification" to ...))` explicitly on the builder, which
matches what `architecture.definition.md` § 4.5 already says ("the resolver sets a
custom classification extension").

Also fixed a test-isolation bug: the `@GraphQlTest` slice's stub use-case bean is
a Spring singleton shared across all test methods in the class, so state set by
one test leaked into the next; added `@BeforeEach` to reset it.

Dispatched real `ddd-hex-reviewer` + `spec-documenter` for the first time this
session (see Iteration 12 — returned DRIFT, resolved there).

### Iteration 10 — Contracts: port-spec reconciliation
- No new RED/GREEN — verified the already-built `RegisterMasterLeasingContractCommand`,
  `MlcConfigurationCommand`, `RegisterMasterLeasingContractResult`, and
  `MasterLeasingContractRepository` against
  `register-master-leasing-contract.inport.spec.md` and
  `master-leasing-contract-repository.outport.spec.md` field-for-field. No
  conflicts found; both specs already matched the code as built since iteration 1.
- Ran the final gates fresh: `./gradlew clean test` — SUCCESSFUL; `./gradlew build`
  — SUCCESSFUL (spotlessCheck, detekt, test, check all passed).
- Not ticked: "Quality gates green" — no ArchUnit suite exists yet
  (`test.definition.md` § 7 item 5 requires the suite to exist and pass; there is
  nothing to run). "ddd-hex-reviewer returns PASS" and "spec reconciled by
  spec-documenter" — left unticked on principle: both named agents were
  unavailable all ten iterations (org spend limit), and I substituted a
  self-review each iteration, which is not the same evidence the DoD names.

### Iteration 9 — AC-04 domain half (I-09 ordering)
- RED: `PriceRangeTest.throwsInvalidMasterLeasingContractException_whenMinExceedsMax`
  — `AssertionError: Expecting code to raise a throwable`.
- GREEN: added the I-09 min<=max guard to `PriceRange`'s init block (after the
  currency-match check), throwing `InvalidMasterLeasingContractException`.
- REFACTOR: `spotlessApply` (formatting only); `spotlessCheck detekt test` green.
- Drift review: PASS (self-verified — agent unavailable).
- No DoD box ticked: AC-04 still needs its GraphQL half
  (`RegisterMasterLeasingContractGraphQLControllerTest.register_returnsBadRequest_whenPriceBandInverted`
  — no GraphQL layer exists yet). Zero-delta iteration.

### Iteration 8 — I-03 (initial configuration version must be 1)
- RED: `MasterLeasingContractTest.register_throwsInvalidMasterLeasingContractException_whenInitialVersionIsNotOne`
  — `AssertionError: Expecting code to raise a throwable`.
- GREEN: added the I-03 guard to `MasterLeasingContract.register`.
- REFACTOR: `spotlessApply` (formatting only); `spotlessCheck detekt test` green.
- Drift review: PASS (self-verified — agent unavailable).
- Spec reconciliation: "Domain invariants... covered by MasterLeasingContractTest"
  ticked (I-02, I-03 — the register-scoped invariants — now both tested there;
  activate/cancel/amend invariants are other use cases' concern). "Driver
  orchestration and event emission... " ticked on existing evidence — both
  `RegisterMasterLeasingContractDriverTest` cases already demonstrated this.
  AC-07 itself remains unticked (still needs its GraphQL half —
  `RegisterMasterLeasingContractGraphQLControllerTest.schema_exposesNoVersionField`
  — no GraphQL layer exists yet).

### Iteration 6 — I-01 (blank partner number rejected)
- RED: `PartnerNumberTest.throwsInvalidMasterLeasingContractException_whenBlank` —
  `AssertionError: Expecting code to raise a throwable`.
- GREEN: added the I-01 guard to `PartnerNumber`'s init block.
- REFACTOR: `spotlessApply` (formatting only); `spotlessCheck detekt test` green.
- Drift review: PASS (self-verified — agent unavailable).
- No DoD box ticked directly (contributes to the broader "every failure scenario"
  line closed in iteration 7); zero-delta iteration.

### Iteration 7 — "Every failure scenario in § 8 has a negative test"
- RED: `MoneyTest.compareTo_throwsIllegalArgumentException_whenCurrenciesDiffer` —
  passed on first run (unexpected). Finding: `Money.compareTo`'s currency guard
  already existed (iteration 1). Vacuity check: mutated `compareTo` to drop the
  `require`; test failed as expected; reverted; suite green again.
- RED: `RegisterMasterLeasingContractDriverTest.register_throwsIllegalArgumentException_whenParentIdIsMalformed`
  — passed on first run (unexpected). Finding: `UUID.fromString` already guards
  malformed parent ids (iteration 1). Vacuity check: mutated the driver to use
  `UUID.randomUUID()` instead of parsing the input; test failed as expected;
  reverted; suite green again.
- REFACTOR: `spotlessApply` (formatting only); `spotlessCheck detekt test` green.
- Drift review: PASS (self-verified — agent unavailable).
- Spec reconciliation: all four § 8 failure-scenario rows now have a negative test
  (self-parent, VO invariant, Money/Percentage invariant, malformed ID). Box ticked.

### Iteration 5 — AC-06 (Notice Period Bounds)
- RED: `NoticePeriodTest` — 2 of 4 cases failed (0 and 37 did not throw); `AssertionError:
  Expecting code to raise a throwable` at each.
- GREEN: added the I-12 guard (1..36 inclusive) to `NoticePeriod`'s init block,
  throwing `InvalidMasterLeasingContractException`.
- detekt caught a `MagicNumber` violation on the literal `36`; fixed by naming
  `MIN_MONTHS`/`MAX_MONTHS` constants.
- REFACTOR: `spotlessApply` (formatting only); `spotlessCheck detekt test` green.
- Drift review: PASS (self-verified — agent unavailable).
- Spec reconciliation: AC-06 ticked. No conflicts found.

### Iteration 4 — AC-05 (Mixed Currency Rejected)
- RED: `PriceRangeTest.throwsIllegalArgumentException_whenCurrenciesDiffer` —
  `AssertionFailedError: Expecting code to raise a throwable` (PriceRange accepted
  mismatched currencies).
- GREEN: added a currency-match `require` to `PriceRange`'s init block, throwing
  `IllegalArgumentException`.
- REFACTOR: `spotlessApply` (formatting only); `spotlessCheck detekt test` green.
- Drift review: PASS (self-verified — agent unavailable).
- Spec reconciliation: AC-05 ticked. No conflicts found.

### Iteration 3 — AC-02 (Affiliated Contract)
- RED: `MasterLeasingContractTest.register_storesParentReference` — passed on first
  run (unexpected). Finding: `register` already stores the parent id generically
  (built in iteration 1), so the gap was coverage, not behaviour (`tdd.definition.md`
  § 2.2).
- Vacuity check: mutated `register` to hardcode `parentMasterLeasingContractId = null`;
  the named test failed as expected; reverted; tree confirmed back to the correct
  state; full suite green again.
- REFACTOR: `spotlessApply` (formatting only); `spotlessCheck detekt test` green.
- Drift review: PASS (self-verified — agent unavailable).
- Spec reconciliation: AC-02 ticked. No conflicts found.

### Iteration 2 — AC-03 (Self-Parenting Rejected)
- RED: `MasterLeasingContractTest.register_throwsInvalidMasterLeasingContractException_whenParentIsSelf`
  — compile failure, `InvalidMasterLeasingContractException` unresolved.
- GREEN: added `InvalidMasterLeasingContractException` and the I-02 guard in
  `MasterLeasingContract.register` (throws before the aggregate is constructed).
- REFACTOR: `spotlessApply` (formatting only); `spotlessCheck detekt test` green.
- Drift review: PASS (self-verified — agent unavailable).
- Spec reconciliation: AC-03 ticked. No conflicts found.

### Iteration 1 — AC-01 (Happy Path)
- RED: `MasterLeasingContractTest.register_createsContractInDraft` — compile failure,
  every referenced type unresolved (Money, EmployerId, LessorId, PartnerNumber,
  MlcConfiguration, ConfigurationVersion, InheritanceMode, ContractType, SalesChannel,
  CreditLimit, PriceRange, EligibleEmployees, ReturnQuota, NoticePeriod,
  MasterLeasingContract, MasterLeasingContractStatus, MasterLeasingContractRegistered).
- GREEN: built the shared kernel (Money, Percentage, EmployerId, LessorId, DomainEvent),
  the masterleasing value objects/enums/entity, and the MasterLeasingContract aggregate
  (`register` + `pullDomainEvents` only — no `activate`/`amendConfiguration`/`cancel`/
  `reconstitute` yet).
- RED: `RegisterMasterLeasingContractDriverTest.register_savesAndPublishes` — compile
  failure, `RegisterMasterLeasingContractDriver` unresolved.
- GREEN: built the inport command/result/usecase triple, the
  `MasterLeasingContractRepository` outport, `ClockPort`/`DomainEventPublisher`
  (shared.outport), and `RegisterMasterLeasingContractDriver`.
- REFACTOR: `./gradlew spotlessApply` (formatting only, no assertions touched);
  `./gradlew spotlessCheck detekt test` green.
- Drift review: PASS (self-verified — `ddd-hex-reviewer` agent unavailable, org spend
  limit hit; see Closeout note).
- Spec reconciliation: AC-01 ticked in
  `documentation/use-cases/uc01-register-master-leasing-contract.spec.md` § 10.
  No conflicts found between `register-master-leasing-contract.inport.spec.md` /
  `master-leasing-contract-repository.outport.spec.md` and the code as built.
- Deliberately deferred: I-01, I-02, I-03, I-09–I-14 invariant guards; GraphQL layer;
  persistence adapter; Flyway migration; ArchUnit suite. Each is its own future DoD
  criterion.
