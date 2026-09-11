# Use Case Specification – <UseCaseName>

<!--
STRUCTURE IS CONTRACTUAL.

Section numbers and headings are fixed. `/loop-uc` parses § 10 to decide when the
use case is finished, and `spec-documenter` reconciles this file against the code
on every increment — both rely on the numbering below. Do not insert, reorder or
renumber sections. A section that does not apply says so explicitly:
"Not applicable — <reason>".

Note on Test Requirements: this template carries no separate "Test Requirements"
section. Required tests are expressed as Definition of Done items in § 10, where
each one names the test that satisfies it. One list, one place
(`file-usage.definition.md` § 4).
-->

## Status
SPECIFIED | IMPLEMENTED | SUPERSEDED

## Bounded Context
`<context>` — triggered via <GraphQL mutation by an external client | domain event | synchronous inport call from another context>.
(For cross-context: Owner: `<context>`. Trigger/Caller: `<context>`. Integration pattern: <domain event | shared-transaction inport call>.)

Must be a context registered in `architecture.definition.md` § 11.

## Purpose
Describe orchestration logic.


## 1. Intent

What business outcome does this use case produce? Name the German term for the
concept if the ubiquitous language has one (`CLAUDE.md`, Ubiquitous Language).


## 2. Input Contract

Fields:
- fieldA
- fieldB

Validation rules:
- Required fields
- Format rules

State explicitly where any timestamp comes from (`architecture.definition.md` § 8.1).
A GraphQL-triggered use case takes it from `ClockPort` and the input type has no
such field. Business dates that the parties agreed — `termStart`, `activationDate` —
are inputs and are validated as business values, not observations.


## 3. Output Contract

Return type:
- Success payload

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `<DomainException>` | <when it is thrown> | `BAD_REQUEST` / `NOT_FOUND` / `CONFLICT` / `INTERNAL_ERROR` |

There are no HTTP status codes. Every GraphQL response is `200 OK`; the error kind
is carried in `extensions.classification` (`architecture.definition.md` § 4.5).


## 4. Preconditions

- Aggregate must exist?
- Must be in a specific state?


## 5. Flow

1. Load aggregate
2. Call domain method
3. Persist aggregate
4. Publish event (if applicable)

Where order is load-bearing — a validation that must precede a write, a fetch that
must precede a construction — say so and say why. `test.definition.md` § 2.2
requires the order to be asserted when it matters.


## 6. Side Effects

- Persistence
- External calls
- Event publication

Name the Flyway migration if the schema changed, and state whether the event is
context-local or lives in `shared.domain.event` and why
(`modelling.definition.md`, Domain Event).


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
A criterion about money or a derived date **states the expected value**
(`sdd.playbook.md` § 4.1).


## 8. Failure Scenarios

| Scenario | Exception | Classification |
|----------|-----------|----------------|
| Aggregate not found | `<NotFoundException>` | `NOT_FOUND` |
| Invalid state transition | `<InvalidStateException>` | `CONFLICT` |
| External dependency failure | `<UnavailableException>` | `INTERNAL_ERROR` |


## 9. GraphQL Contract

For non-GraphQL use cases: `Not applicable — <event-driven | inport-triggered>.`

Schema (`src/main/resources/graphql/<context>/<name>.graphqls`):
```graphql
type Mutation {
  <operation>(input: <Name>Input!): <Name>Payload!
}
```

Operation:
```graphql
mutation { }
```

Response:
```json
{ "data": { } }
```

Error classification mapping:
- success – <condition>
- `BAD_REQUEST` – <validation failure>
- `NOT_FOUND` – <missing aggregate>
- `CONFLICT` – <invalid state transition>

Every classification listed here MUST have a matching operation in
`graphql/uc<nn>-<use-case-name>.graphql` (`CLAUDE.md`, GraphQL Operation
Documentation).


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
- [ ] `src/main/resources/graphql/<context>/<name>.graphqls` declares the operation
- [ ] `graphql/uc<nn>-<use-case-name>.graphql` covers success and every classification in § 9
- [ ] Persistence roundtrip covered by `<Aggregate>PersistenceAdapterIT` (if persistence changed)
- [ ] Flyway migration `V<n>__DDL_<description>.sql` exists (if the schema changed)
- [ ] Port specs in `documentation/ports/` reflect the ports as implemented

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
