# Use Case Specification – <UseCaseName>

<!--
STRUCTURE IS CONTRACTUAL.

Section numbers and headings are fixed. `/loop-uc` parses § 10 to decide when the
use case is finished, and `spec-documenter` reconciles this file against the code
on every increment — both rely on the numbering below. Do not insert, reorder or
renumber sections. A section that does not apply says so explicitly:
"Not applicable — <reason>".

Note on Test Requirements: this template no longer carries a separate
"Test Requirements" section. Required tests are expressed as Definition of Done
items in § 10, where each one names the test that satisfies it. One list, one
place (`file-usage.definition.md` § 4).
-->

## Status
BLOCKED | SPECIFIED | IMPLEMENTED | SUPERSEDED

<!--
Status vocabulary. Exactly one value, on the line above, as the whole line — the
`/loop-uc` preflight parses it.

- `BLOCKED`   — structurally complete and **not workable**: at least one open question
                reaches a section the implementation needs. `/loop-uc` refuses to start.
                List the questions in § 10 so the refusal names them.
- `SPECIFIED` — workable. Every question that blocks an implementable section is closed.
- `IMPLEMENTED`
- `SUPERSEDED` — `/loop-uc` refuses; point at the specs that replaced it.

**Why `BLOCKED` exists.** `uc07-create-master-leasing-contract.spec.md` was written with
eleven unanswered questions and the status `SPECIFIED`, because the vocabulary had no value
for "specified but not workable". Its § 10 said in prose *"do not start the loop"* — an
instruction to a human that no parser could see, sitting in the one document the loop reads
every iteration. A rule with no executable owner does not survive contact with a refactor
(ADR-0014), so the state got a value and the value got a gate.
-->

## Bounded Context
`<context>` — triggered via <GraphQL by external client | REST by external client | event-driven | synchronous outport call>.
(For cross-context: Owner: `<context>`. Trigger/Caller: `<context>`. Integration pattern: <event-driven | synchronous outport call>.)

Must be a context registered in `architecture.definition.md` § 11.

## Purpose
Describe orchestration logic.


## 1. Intent

**Source:** `<TICKET-KEY>` | `n/a — example use case`

The ticket or document this specification was derived from. `/spec-create` fills it; a
spec written by hand still needs it, or `n/a` with a reason.

It is not bookkeeping. The conformance axis of `/code-review` follows this field back to
the original request to answer "did we build what was asked" — without it, that check can
only compare the code against the spec, which verifies the spec against itself.

What business outcome does this use case produce?


## 2. Input Contract

Fields:
- fieldA
- fieldB

Validation rules:
- Required fields
- Format rules


## 3. Output Contract

Return type:
- Success payload

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `<DomainException>` | <when it is thrown> | <4xx/5xx> |


## 4. Preconditions

- Aggregate must exist?
- Must be in specific state?


## 5. Flow

1. Load aggregate
2. Call domain method
3. Persist aggregate
4. Publish event (if applicable)


## 6. Side Effects

- Persistence
- External calls
- Event publication


## 7. Acceptance Criteria

Each criterion carries a stable `AC-NN` identifier. Tests cite it, and § 10
holds at least one item accountable for it (`sdd.playbook.md` § 4.1–4.2).

**AC-01 – <short name>**
Given <initial state>
When <action>
Then <observable outcome>

**AC-02 – <short name>**
Given
When
Then

Criteria describe domain behaviour, state transitions, failure scenarios and
invariant enforcement. Technical implementation details are not criteria.


## 8. Failure Scenarios

- Aggregate not found
- Invalid state
- External dependency failure


## 9. API Contract

For a use case with no external API: `Not applicable — <event-driven | outport-triggered>.`
UC06 in this repository is the worked example of that answer; a use case whose only
trigger is a domain event has no endpoint, and inventing one to fill this section is worse
than leaving it empty.

List **every** transport the use case is exposed over. A use case may be exposed over one,
both or neither — the core does not know which, and that is the point
(`coding-style.definition.md` § 3.3).

### REST

Endpoint:
```
<METHOD> /<path>
```

Request body:
```json
{ }
```

Response body:
```json
{ }
```

HTTP status mapping:
- `200 OK` / `201 Created` – <success condition>
- `400 Bad Request` – <validation failure>
- `404 Not Found` – <missing aggregate>
- `409 Conflict` – <invalid state transition>
- `502 Bad Gateway` – <a dependency failed>

### GraphQL

Operation:
```graphql
<query | mutation> <name>(input: <InputType>!): <PayloadType>!
```

Error classification:
- `BAD_REQUEST` – <validation failure and, since GraphQL has no conflict type, state conflicts>
- `NOT_FOUND` – <missing aggregate>
- `INTERNAL_ERROR` – <a dependency failed>

GraphQL's error vocabulary is coarser than HTTP's. Where two HTTP statuses collapse into
one classification, **say so here** — a client author will hit that asymmetry, and the
place to learn about it is the contract, not a support conversation.

### Executable requests

Every status and every error classification listed above MUST have a matching request in
`api/`:

- `api/uc<nn>-<use-case-name>.http` for REST
- `api/uc<nn>-<use-case-name>.graphql` for GraphQL

Only for the transports this use case actually uses. These files are the cheap check that
this section and the code still agree, and `spec-documenter` verifies they exist
(`CLAUDE.md`, API Contract Documentation).


## 10. Definition of Done

The **authoritative exit condition** for `/loop-uc` (`loop.playbook.md` § 1).
`tasks.md` mirrors this list; the spec always wins.

Rules for writing items:

- Every `AC-NN` in § 7 is cited by at least one item.
- Every item is objectively checkable: a named passing test, an existing file, a
  `PASS` verdict, a green gate. "Code is clean" is not an item.
- Test-backed items name the test — `covered by <TestClass>.<method>`, not
  "implemented" (`test.definition.md` § 9).

### Behaviour
- [ ] AC-01 covered by `<TestClass>.<test_method>`
- [ ] AC-02 covered by `<TestClass>.<test_method>`
- [ ] Domain invariants for `<Aggregate>` covered by `<AggregateTest>`
- [ ] Driver orchestration and event emission covered by `<UseCase>DriverTest`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `api/uc<nn>-<use-case-name>.http` covers every status in § 9 (or § 9 says REST is not applicable)
- [ ] `api/uc<nn>-<use-case-name>.graphql` covers every classification in § 9 (or § 9 says GraphQL is not applicable)
- [ ] Persistence roundtrip covered by `<Aggregate>JpaRepositoryIT` (if persistence changed)
- [ ] Port specs in `documentation/ports/` reflect the ports as implemented

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` on the architecture axis
- [ ] `conformance-reviewer` matches every § 7 criterion to a test and every § 10 box to an artifact
- [ ] Quality gates green (`test.definition.md` § 7)
