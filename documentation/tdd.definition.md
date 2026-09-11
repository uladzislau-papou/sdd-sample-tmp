# TDD Definition – JRL Contract Management (`tdd.definition.md`)

## Purpose

`test.definition.md` defines **what** a test asserts.
This document defines **when** it is written: before the code.

Test-Driven Development is the implementation discipline of this project. It is
not a preference and not a style. In a codebase whose central claim is
*Always-Valid domain models*, a test written after the fact can only confirm
what the code happens to do. A test written first is the only artefact that can
state what the domain is *required* to do — which makes it the executable form of
the Acceptance Criteria in the Use Case Spec.

This document refines `test.definition.md` and MUST NOT restate its taxonomy,
assertion rules, or quality gates.

---

# 1. The Cycle

One DoD criterion at a time. Three steps, in order, no skipping.

```
        ┌──────────────────────────────────────────────┐
        │                                              │
        ▼                                              │
   ┌─────────┐      ┌───────────┐      ┌──────────┐    │
   │   RED   │─────▶│   GREEN   │─────▶│ REFACTOR │────┘
   │ failing │      │  minimal  │      │  no new  │
   │  test   │      │   code    │      │ behaviour│
   └─────────┘      └───────────┘      └──────────┘
```

## 1.1 RED — write the failing test

Write exactly one test that expresses the next unmet criterion, then **run it
and watch it fail**.

Exit condition: the test fails, and it fails **for the intended reason**.

A test that fails because a class does not compile is a valid RED. A test that
fails because of a typo in the test itself is not — fix it and re-run.

In Kotlin the compile failure is the *normal* first RED, because a call to a
function that does not exist yet does not compile. That is fine and is not a
lesser form of RED: the compiler is asserting the same absence the test is. What
it is not is an excuse to skip running the command — the quoted output is the gate
(§ 2), and "it obviously will not compile" is a prediction.

## 1.2 GREEN — make it pass, minimally

Write the least production code that turns this test green.

Exit condition: the new test passes **and no previously passing test broke**.

"Minimally" is a real constraint, not modesty. If a use case has five failure
scenarios, GREEN for `AC-02` implements `AC-02` only. The other four arrive in
their own cycles. Implementing ahead of the test is how untested branches enter
a codebase that believes it is fully covered.

Kotlin makes one form of over-implementation especially tempting: a `when` over a
sealed type or enum will not compile until every branch is handled. Handling the
others with a `TODO()` is correct; implementing them because the compiler asked is
over-implementing during GREEN.

## 1.3 REFACTOR — improve the shape, not the behaviour

With the suite green, improve naming, extract value objects, collapse
duplication, move logic to where the ontology says it belongs.

Exit condition: the whole suite is still green and **no test was modified**.

Changing a test during REFACTOR means the behaviour changed, which means it was
not a refactor. Back it out and do it as its own RED.

---

# 2. The RED Evidence Rule

**An agent MUST NOT write production code until it has run the new test and
quoted the actual failure.**

This is the load-bearing rule of this document. Everything else is guidance;
this is a gate. Without it, "TDD" degrades into writing tests and code in the
same breath and asserting afterwards that the order was correct.

Required evidence, quoted from the real run:

- the command (`./gradlew test --tests '<FQCN>'`)
- the failing test name
- the assertion message or exception, verbatim

```
> ./gradlew test --tests '*MasterLeasingContractTest.activate_throwsInvalidMasterLeasingContractStateException_whenAlreadyCancelled'

MasterLeasingContractTest > activate_throwsInvalidMasterLeasingContractStateException_whenAlreadyCancelled FAILED
    java.lang.AssertionError:
    Expecting code to raise a throwable.
```

Rules:

- Paraphrased, predicted, or reconstructed failures do not count. If the output
  was not seen, the gate was not passed.
- A RED step that unexpectedly **passes** is a finding, not a formality: either
  the behaviour already exists (close the criterion, note it) or the test does
  not actually test what it claims (fix the test).
- The evidence belongs in the increment's Closeout report under
  `Verification` — see `execution.playbook.md` § 5.

## 2.1 Exception: behaviour-preserving changes

A change with **no behavioural delta** cannot produce a RED — there is no new behaviour
to fail on. Moving a class between packages, renaming one, relocating an adapter, or
extracting a function are all this shape.

For these, the evidence is the **unchanged suite**:

- the test method count is identical before and after,
- every test that passed still passes,
- **no test file was modified** beyond imports and references forced by the move,
- and the report states explicitly that this is a behaviour-preserving change, with the
  before/after counts.

That last point is the whole safeguard. "No behavioural delta" is a claim the author
makes, and it is the claim a reviewer checks. An agent that invokes this clause while
actually changing behaviour has done something worse than skipping RED — it has asserted
a property the suite was never asked to verify.

Boundaries of the exception:

- It covers **production** code that moves. It does not license new production code.
- If the change adds, removes or alters a branch, it is **not** behaviour-preserving,
  regardless of whether existing tests still pass. Go through RED.
- If an existing test must change its *assertions* to accommodate the change, the
  behaviour changed. Back it out and do it as its own RED.
- Adding a test to previously-uncovered code is not this clause either — see § 2.2.
- **A Flyway migration is never behaviour-preserving.** Adding a column changes what
  `ddl-auto=validate` accepts at startup and what a round-trip carries, and both are
  observable. It gets a RED, in the persistence suite.

## 2.2 A RED that unexpectedly passes

Writing a test for existing-but-untested code will often go green immediately. That is
not a failure of the process and not something to disguise; it is a **finding**:

- **the behaviour already exists** → close the criterion, and record that the gap was
  *coverage*, not behaviour; or
- **the test does not test what it claims** → fix the test.

When a batch of such tests all pass first time, prove they are not vacuous
(`test.definition.md` § 8 forbids "green tests that do not assert anything meaningful").
The cheapest proof is a temporary mutation of the production code: break the behaviour,
confirm a named test fails, restore, confirm the tree is byte-identical. Report which
mutation was applied and which tests caught it.

Good mutations in this domain, because they are the defects that actually occur:
flip a comparison boundary (`>` to `>=`) on a credit-limit or price-band check;
change a `Money` scale from 4 to 2; swap `termStart` and `termEnd`; drop a column
from an `update`.

---

# 3. Ordering Across the Layers

The package ontology in `architecture.definition.md` § 3 dictates the order.
Work **inside-out**: the domain first, then outward through the ports.

| Step | Test (per `test.definition.md` § 2) | Production target |
|------|------------------------------------|-------------------|
| 1 | Domain test (§ 2.1) — invariants, state transitions, negative cases | `<context>.core.domain.<aggregate>` |
| 2 | Use case test (§ 2.2) — orchestration, events, failure paths, ports stubbed | `<context>.inbound.driver` + the `core.inport` triple |
| 3 | GraphQL test (§ 2.4) — operation, argument validation, error classification | `<context>.inbound.graphql`, the schema file, **and** `graphql/uc<nn>-*.graphql` |
| 4 | Adapter integration test `*IT` (§ 2.3) — roundtrip, scale, query semantics | `<context>.outbound.persistence` + the Flyway migration |

Why inside-out rather than outside-in: in this architecture the invariants *are*
the product. Driving from the transport edge inward produces adapters that work and
aggregates that merely comply, and it invites the anemic model that
`modelling.definition.md` forbids. Starting at the aggregate forces the domain
language to be settled before anything depends on it.

Deviation is allowed but must be justified in the increment's report — e.g. a
persistence-only change legitimately starts at step 4.

New outbound ports: write the use case test with a **stub** implementation first
(`test.definition.md` § 2.2), then the real adapter with its own `*IT`.

**Cross-context use cases run the ladder twice.** UC04 cancels a master contract and
terminates the leases under it. The domain step is done in each context separately —
`MasterLeasingContract.cancel`, then `IndividualLeasingContract.terminate` — before
either driver exists. Driving from the orchestrating driver instead produces two
aggregates shaped by one caller's convenience.

---

# 4. Granularity

- One RED → GREEN → REFACTOR cycle per **DoD criterion**, not per class and not
  per use case.
- A cycle should be small enough to hold in one head. If GREEN needs more than a
  handful of files, the criterion was too coarse — split it.
- Never leave the tree with a failing test at the end of a cycle. RED is a state
  you pass *through*, not a state you hand over.

---

# 5. Anti-Patterns (Strictly Forbidden)

These are the specific ways TDD is faked. Each is a `DRIFT`-level finding.

- **Test-after.** Writing production code, then a test that describes it.
  Detectable and detected: no quoted RED failure means no RED.
- **Weakening the test to reach green.** Loosening an assertion, widening an
  expected exception type to a supertype, deleting a case, or relaxing a boundary
  value because the implementation disagreed with it. If a test is wrong, fix it as
  its own RED with the reason stated — never silently, and never while chasing green.
- **Relaxing a monetary assertion to `isCloseTo`** because the implementation
  rounds differently. The test was right; the rounding is the defect.
- **`@Disabled` / `@Ignore`.** Already forbidden by `test.definition.md` § 7.
  A test that cannot pass is either a spec defect or an unfinished increment;
  both are reportable, neither is skippable.
- **Widening `PostgresAvailability`'s skip** to cover a test that is failing rather
  than one whose database is absent. That converts a red test into a silent skip,
  which is the worst available outcome (`test.definition.md` § 2.3).
- **Over-implementing during GREEN.** Building the whole use case while one
  criterion is red. Leaves untested branches behind a green suite.
- **Modifying a test during REFACTOR.** See § 1.3.
- **Commenting out an assertion**, even temporarily.
- **Asserting on what the implementation does** rather than what the criterion
  requires — writing the test by reading the code.
- **A green cycle with no quoted RED.** The cycle did not happen.

---

# 6. Relationship to the Specs

TDD is what makes the traceability chain in `sdd.playbook.md` § 3 executable
rather than aspirational:

```
AC-NN (spec § 7) ──▶ RED test ──▶ GREEN code ──▶ DoD item ticked (spec § 10)
```

- Every `AC-NN` in a Use Case Spec becomes at least one RED test. The test name
  should make the link obvious (`test.definition.md` § 5.1).
- A DoD criterion is tickable only when a **named, passing** test backs it.
  "Implemented" is not a DoD state; "covered by `<TestClass>.<method>`" is.
- A criterion with no test that can express it is an under-specified criterion.
  Fix the spec — do not tick the box. This is the condition the outer loop's
  no-progress detector exists to surface (`loop.playbook.md`).

---

# 7. Verification

The cycle itself is verified by the increment's report, not by a tool. An
increment is TDD-compliant when its Closeout shows, per criterion:

1. the quoted RED failure (§ 2),
2. the passing test after GREEN,
3. the quality gates from `test.definition.md` § 7 green after REFACTOR.

Missing item 1 invalidates the increment regardless of how green items 2 and 3
are.
