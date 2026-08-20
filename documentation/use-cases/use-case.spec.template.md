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
SPECIFIED | IMPLEMENTED | SUPERSEDED

## Bounded Context
`<context>` — triggered via <REST by external client | event-driven | synchronous outport call>.
(For cross-context: Owner: `<context>`. Trigger/Caller: `<context>`. Integration pattern: <event-driven | synchronous outport call>.)

Must be a context registered in `architecture.definition.md` § 11.

## Purpose
Describe orchestration logic.


## 1. Intent

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


## 9. REST Contract

For non-REST use cases: `Not applicable — <event-driven | outport-triggered>.`

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

Every status listed here MUST have a matching request in
`rest/uc<nn>-<use-case-name>.http` (`CLAUDE.md`, REST Endpoint Documentation).


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
- [ ] `rest/uc<nn>-<use-case-name>.http` covers every status in § 9
- [ ] Persistence roundtrip covered by `<Aggregate>JooqRepositoryIT` (if persistence changed)
- [ ] Port specs in `documentation/ports/` reflect the ports as implemented

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
