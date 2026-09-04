# TDD Definition – Risk Management Service (`tdd.definition.md`)

## Purpose

`test.definition.md` defines **what** a test asserts. This document defines **when** it is
written: before the code.

RMS is a **brownfield** codebase. Most of it was not written test-first, and this document
does not pretend otherwise. What it governs is **new work**: every increment from here
starts with a failing test, and the failure is quoted as evidence.

Why the rule is worth keeping in a codebase that did not always follow it: the failures
RMS can least afford are silent ones — an unmapped provider status, a transition that
should have been rejected, an audit event that stopped firing. A test written after the
code confirms what the code does. A test written first states what the contract requires.
For a service whose output is regulated decisions, that difference is the whole point.

This document refines `test.definition.md` and MUST NOT restate its taxonomy, assertion
rules, or quality gates.

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

Write exactly one test expressing the next unmet criterion, then **run it and watch it
fail**.

Exit condition: the test fails, and it fails **for the intended reason**.

A test that fails because a class does not compile is a valid RED. A test that fails
because of a typo in the test is not — fix it and re-run.

## 1.2 GREEN — make it pass, minimally

Write the least production code that turns this test green.

Exit condition: the new test passes **and no previously passing test broke**.

"Minimally" is a real constraint. If a service has five rejection paths, GREEN for `AC-02`
implements `AC-02` only. Implementing ahead of the test is how untested branches enter a
codebase that believes it is covered.

## 1.3 REFACTOR — improve the shape, not the behaviour

With the suite green: improve naming, extract private steps, move logic to the layer the
ontology assigns it, delete duplication.

Exit condition: the whole suite is green and **no test was modified**.

Changing a test during REFACTOR means the behaviour changed, which means it was not a
refactor. Back it out and do it as its own RED.

---

# 2. The RED Evidence Rule

**An agent MUST NOT write production code until it has run the new test and quoted the
actual failure.**

This is the load-bearing rule. Everything else is guidance; this is a gate. Without it,
"TDD" degrades into writing tests and code in the same breath and asserting afterwards
that the order was correct.

Required evidence, quoted from the real run:

- the command
- the failing test name
- the assertion message or exception, verbatim

```
> ./gradlew test --tests '*KycCaseDecisionServiceTest.acceptCase_throwsBadUserInput_whenCaseIsDeclined'

KycCaseDecisionServiceTest > acceptCase_throwsBadUserInput_whenCaseIsDeclined FAILED
    java.lang.AssertionError:
    Expecting code to raise a throwable.
```

Rules:

- Paraphrased, predicted, or reconstructed failures do not count. If the output was not
  seen, the gate was not passed.
- A RED that unexpectedly **passes** is a finding, not a formality — see § 2.2.
- The evidence belongs in the increment's Closeout report under `Verification`
  (`execution.playbook.md` § 5).

Note: this is a single-module Gradle build. The task is `test`, not `:app:test`.

## 2.1 Exception: behaviour-preserving changes

A change with **no behavioural delta** cannot produce a RED. Moving a class between
packages, renaming one, extracting a private method, converting an assertion style are all
this shape.

For these, the evidence is the **unchanged suite**:

- the test method count is identical before and after,
- every test that passed still passes,
- **no test file was modified** beyond imports and references forced by the move,
- and the report states explicitly that this is a behaviour-preserving change, with the
  before/after counts.

That last point is the safeguard. "No behavioural delta" is a claim the author makes and a
reviewer checks. Invoking this clause while actually changing behaviour is worse than
skipping RED — it asserts a property nothing verified.

Boundaries:

- It covers **production** code that moves. It does not license new production code.
- If the change adds, removes or alters a branch, it is **not** behaviour-preserving,
  regardless of whether existing tests still pass. Go through RED.
- If an existing test must change its *assertions*, the behaviour changed.
- Adding a test to previously-uncovered code is not this clause — see § 2.2.

## 2.2 A RED that unexpectedly passes

In a brownfield codebase this will happen often: you write a test for behaviour that
already exists but was never covered. That is **not** a failure of the process and not
something to disguise. It is a finding:

- **the behaviour already exists** → close the criterion, and record that the gap was
  *coverage*, not behaviour; or
- **the test does not test what it claims** → fix the test.

When a batch of such tests all pass first time, prove they are not vacuous
(`test.definition.md` § 8 forbids green tests that assert nothing meaningful). The cheapest
proof is a temporary mutation: break the production behaviour, confirm a named test fails,
restore, confirm the tree is byte-identical. Report which mutation was applied and which
tests caught it.

## 2.3 Retrofitting coverage before changing behaviour

**Before modifying an untested service method, write a test for its current behaviour
first, and land it as its own step.**

This is the brownfield companion to the RED rule. A characterization test that passes
immediately (§ 2.2) is the safety net that makes the subsequent behaviour change
reviewable: the diff then shows exactly which assertion changed and why.

Do not skip it on the grounds that the change is small. The changes that broke this class
of service were all small.

---

# 3. Ordering Across the Layers

`architecture.definition.md` § 3 dictates the order. Work **inward-out from the business
rule**: the service first, then the edges.

| Step | Test (per `test.definition.md` § 2) | Production target |
|------|-------------------------------------|-------------------|
| 1 | Service test (§ 2.1) — happy path, every rejection path, side effects | `<module>/service/` |
| 2 | Mapper test (§ 2.2) — every enum value, unknown-input failure | `<module>/api/mapper/` |
| 3 | Controller test (§ 2.3) — error classification, validation | `<module>/api/controller/` **and** `rest/uc<nn>-*.http` |
| 4 | Authorization test (§ 2.4) — 401 / 403 / pass | `<module>/web/auth/` or root `web/auth/` |
| 5 | Integration test (§ 2.5) — query semantics, migration effect, round-trip | `<module>/repository/`, `db/migration/` |

Why service-first rather than controller-first: in RMS the business rules live in services
(`modelling.definition.md` § 1). Driving from the GraphQL edge inward produces controllers
that work and services that merely comply, and it puts assertions about transition legality
at the most expensive layer to run them.

Deviation is allowed but must be justified in the report — a migration-only change
legitimately starts at step 5; a provider-contract change legitimately starts at step 2.

For a **new provider integration**, insert a client test with a stubbed HTTP layer between
steps 2 and 3, and write the mapper test before the client.

---

# 4. Granularity

- One RED → GREEN → REFACTOR cycle per **DoD criterion**, not per class and not per use
  case.
- A cycle should be small enough to hold in one head. If GREEN needs more than a handful of
  files, the criterion was too coarse — split it.
- Never leave the tree with a failing test at the end of a cycle. RED is a state you pass
  *through*, not a state you hand over.

---

# 5. Anti-Patterns (Strictly Forbidden)

Each is a `DRIFT`-level finding.

- **Test-after.** Writing production code, then a test that describes it. No quoted RED
  means no RED.
- **Weakening the test to reach green.** Loosening an assertion, widening an expected
  exception type, deleting a case, removing a `verify`. If a test is wrong, fix it as its
  own RED with the reason stated — never silently, and never while chasing green.
- **`@Disabled` / `@Ignore`.** Forbidden by `test.definition.md` § 7. A test that cannot
  pass is either a spec defect or an unfinished increment; both are reportable, neither is
  skippable.
- **`@Suppress` to get past `allWarningsAsErrors` or detekt** instead of fixing the cause.
- **Over-implementing during GREEN.** Building the whole use case while one criterion is
  red.
- **Modifying a test during REFACTOR.**
- **Commenting out an assertion**, even temporarily.
- **Asserting on what the implementation does** rather than what the criterion requires —
  writing the test by reading the code.
- **Modifying an untested service without a characterization test first** (§ 2.3).
- **Closing a criterion with an integration test that was skipped** because the database
  was unavailable (`test.definition.md` § 1.3).
- **A green cycle with no quoted RED.** The cycle did not happen.

---

# 6. Relationship to the Specs

TDD is what makes the traceability chain in `sdd.playbook.md` § 3 executable rather than
aspirational:

```
AC-NN (spec § 7) ──▶ RED test ──▶ GREEN code ──▶ DoD item ticked (spec § 10)
```

- Every `AC-NN` becomes at least one RED test. The test name makes the link obvious
  (`test.definition.md` § 5.1).
- A DoD criterion is tickable only when a **named, passing** test backs it.
  "Implemented" is not a DoD state; "covered by `<TestClass>.<method>`" is.
- A criterion no test can express is an under-specified criterion. Fix the spec — do not
  tick the box. This is what the outer loop's no-progress detector exists to surface.

---

# 7. Verification

The cycle is verified by the increment's report, not by a tool. An increment is
TDD-compliant when its Closeout shows, per criterion:

1. the quoted RED failure (§ 2), or an explicit § 2.1 / § 2.2 finding,
2. the passing test after GREEN,
3. the quality gates from `test.definition.md` § 7 green after REFACTOR.

Missing item 1 invalidates the increment regardless of how green items 2 and 3 are.
