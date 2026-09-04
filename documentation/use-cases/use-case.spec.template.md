# Use Case Specification – <UseCaseName>

<!--
STRUCTURE IS CONTRACTUAL.

Section numbers and headings are fixed. `/loop-uc` parses § 10 to decide when the
use case is finished, and `spec-documenter` reconciles this file against the code
on every increment — both rely on the numbering below. Do not insert, reorder or
renumber sections. A section that does not apply says so explicitly:
"Not applicable — <reason>".

Required tests are expressed as Definition of Done items in § 10, where each one
names the test that satisfies it. One list, one place
(`file-usage.definition.md` § 4).
-->

## Status
SPECIFIED | IMPLEMENTED | SUPERSEDED

## Module
`<qes | kyc | onb | boni>` — must be a module registered in
`architecture.definition.md` § 11.1.

Trigger: `<GraphQL mutation | GraphQL query | REST endpoint | provider webhook | scheduler | internal call from another module>`

If this use case crosses a module boundary, name the edge and confirm it is registered in
`architecture.definition.md` § 11.2:
`<from> → <to>` — registered: yes/no.

## Purpose
One paragraph. What business outcome this produces and for whom.


## 1. Intent

What business outcome does this use case produce?


## 2. Input Contract

Type: `<module>.api.input.<Name>Input` (or `api.model.<Name>Request` for a webhook)

| Field | Type | Required | Meaning / constraint |
|-------|------|----------|----------------------|
| | | | |

Validation rules (Bean Validation, enforced at the boundary):

-

Rules enforced in the service rather than at the boundary, and why
(`modelling.definition.md` § 3.1):

-


## 3. Output Contract

Success type: `<module>.api.dto.<Name>Dto`

| Field | Type | Meaning |
|-------|------|---------|
| | | |

Error contract:

| Exception | Condition | Surfaced as |
|-----------|-----------|-------------|
| `BadUserInputException` | | GraphQL `BAD_USER_INPUT` / HTTP 400 |
| `EntityNotFoundException` | | GraphQL `NOT_FOUND` / HTTP 404 |
| | | |


## 4. Preconditions

- Which entity must exist?
- Which lifecycle state(s) is this legal from?
- Which scope must the caller hold?
- Must an operator be present, or is a service actor acceptable?


## 5. Flow

1. Controller validates and delegates
2. Service opens the transaction
3. Load `<Entity>` via `<Repository>`
4. Check transition legality (`<state machine | explicit guard>`)
5. Mutate and persist
6. Emit audit event `<event.name>`
7. Enqueue `<outbox delivery>` (if applicable)
8. Map to `<Dto>` and return

Transaction boundary: `<which service method, which propagation>`.


## 6. Side Effects

| Kind | Detail |
|------|--------|
| Persistence | tables written |
| Audit event | event name; catalogue entry in `docs/` |
| Outbound delivery | outbox table + delivery service |
| Provider call | client, endpoint, timeout, retry |
| Application event | published / consumed |

Idempotency: `<is a repeat safe? what makes it safe?>`


## 7. Acceptance Criteria

Each criterion carries a stable `AC-NN` identifier. Tests cite it, and § 10 holds at least
one item accountable for it (`sdd.playbook.md` § 4.1–4.2).

**AC-01 – <short name>**
Given <initial state>
When <action>
Then <observable outcome>

**AC-02 – <short name>**
Given
When
Then

Criteria describe business behaviour, transitions, failure scenarios, authorization and
audit consequences. Technical implementation details are not criteria.


## 8. Failure Scenarios

- Entity not found
- Illegal transition from state X
- Caller lacks the required scope
- Provider unavailable / provider returned an error
- Optimistic-lock conflict (concurrent transition)
- Delivery failure — what is retried, what is surfaced


## 9. API Contract

For non-API use cases (scheduler, internal call):
`Not applicable — <scheduler | internal>.` — then state the trigger cadence or caller.

### GraphQL

Endpoint: `POST /riskmanagement/<module>/v1/graphql`
Scope: `api.<module>.<read|write>`
Schema: `src/main/resources/graphql/<module>/<file>.graphqls`

```graphql
mutation {
  <operation>(input: { }) { }
}
```

### REST

```
<METHOD> /<path>
```
Scope: `<scope>` (or the webhook's own auth mechanism)

Request / response bodies:

```json
{ }
```

Status / classification mapping:

- `<200 | 201 | data>` – success condition
- `BAD_USER_INPUT` / `400` – validation or illegal transition
- `NOT_FOUND` / `404` – missing entity
- `401` – missing or invalid token
- `403` – valid token, wrong scope
- `502` – provider failure

Every outcome listed here MUST have a matching request in
`rest/uc<nn>-<use-case-name>.http` (`CLAUDE.md`, API Request Documentation).


## 10. Definition of Done

The **authoritative exit condition** for `/loop-uc` (`loop.playbook.md` § 1).
`tasks.md` mirrors this list; the spec always wins.

Rules for writing items:

- Every `AC-NN` in § 7 is cited by at least one item.
- Every item is objectively checkable: a named passing test, an existing file, a `PASS`
  verdict, a green gate. "Code is clean" is not an item.
- Test-backed items name the test — `covered by <TestClass>.<method>`, not "implemented"
  (`test.definition.md` § 9).

### Behaviour
- [ ] AC-01 covered by `<ServiceTest>.<method>`
- [ ] AC-02 covered by `<ServiceTest>.<method>`
- [ ] Every rejection path in § 8 has a negative test asserting the exception type
- [ ] Side effects in § 6 verified: persistence captured, audit event asserted, `never()`
      on the rejection paths

### Contracts
- [ ] Every enum mapping introduced is exhaustively covered by `<MapperTest>`
- [ ] `<Controller>Test` covers success and each error classification in § 9
- [ ] Authorization covered by `<FilterOrInterceptorTest>`: 401, 403, pass-through
- [ ] `rest/uc<nn>-<use-case-name>.http` covers every outcome in § 9
- [ ] GraphQL schema and controller agree (no orphan field either way)
- [ ] Integration spec in `documentation/integrations/` reflects the surface as built
- [ ] Migration effect covered by `<IntegrationTest>` (if the schema changed)
- [ ] New config variables present in `.env.example`
- [ ] New/changed audit events present in the `docs/` catalogue

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `rms-architecture-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
