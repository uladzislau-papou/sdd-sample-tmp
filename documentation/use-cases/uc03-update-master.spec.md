# Use Case Specification – UpdateMaster

## Status
SPECIFIED

## Bounded Context
`contract` — triggered via GraphQL by external client.

## Purpose

Change a Master's name, its status, or both.


## 1. Intent

**Source:** `n/a — invented scope. See `project.definition.md`, "Purpose of the service".`

**Business outcome:** a Master's mutable facts can be corrected, and a Master can be taken out
of and back into service without being deleted.

`customerNumber` is **not** updatable. It identifies the Master to the outside world, and a
field that both identifies and changes is the kind of thing that turns into a data-migration
story. Changing it means creating a new Master.


## 2. Input Contract

Fields:
- `masterId`
- `name` — optional
- `status` — optional, `ACTIVE` or `INACTIVE`

Validation rules:
- `masterId` is required and must parse as a UUID.
- `name`, when present, must satisfy I-01 in `aggregate-master.spec.md` § 2.
- `status`, when present, must be one of the two enum values. The GraphQL enum enforces this at
  the transport, so the domain never sees a third value.
- **At least one of `name` and `status` must be present.** A request changing nothing is
  rejected with `InvalidMasterException` rather than silently succeeding.

**Absent and null mean the same thing here: "leave it alone".** This is the ambiguity every
partial-update API has, and it is resolved rather than left to the implementer — neither field
is nullable in the domain, so there is no "set it to nothing" to express.


## 3. Output Contract

Return type:
- `UpdateMasterResult` carrying the Master's `id`, `name`, `customerNumber`, `status` and
  `createdAt` after the change.

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `MasterNotFoundException` | No Master with that id | `404` |
| `InvalidMasterException` | I-01 violated, or neither field supplied | `400` |

No REST endpoint exists (`adr/0020`).


## 4. Preconditions

- The Master must exist.
- No status precondition. An `INACTIVE` Master can be renamed, and either status can be set
  from either — see `aggregate-master.spec.md` § 3.


## 5. Flow

1. Driver parses `masterId`.
2. Driver rejects a request carrying neither `name` nor `status`.
3. Driver calls `MasterRepository.findById`; raises `MasterNotFoundException` if empty.
4. If `name` is present, driver calls `master.rename(MasterName(name))`.
5. If `status` is present, driver calls `master.activate()` or `master.deactivate()`.
6. Driver calls `MasterRepository.save(master)`.
7. Driver publishes `master.pullDomainEvents()` inside the transaction.
8. Driver maps the aggregate to `UpdateMasterResult`.

Steps 3–7 are one transaction.

**The driver chooses the method; the aggregate decides whether anything happened.** Step 5 is
not a transition command — `aggregate-master.spec.md` § 3 makes `ACTIVE → ACTIVE` a no-op that
emits nothing. That is what makes a resubmitted unchanged form harmless.


## 6. Side Effects

- **Persistence:** the `master` row is updated. Contract rows are untouched.
- **External calls:** none.
- **Event publication:** `MasterRenamed` and/or `MasterActivated` / `MasterDeactivated` —
  **only for the fields that actually changed**. An update that sets both fields to their
  current values publishes nothing.


## 7. Acceptance Criteria

**AC-01 – A name change is applied and published**
Given a Master named `Acme`
When `UpdateMaster` is called with the name `Acme Holdings`
Then the persisted name is `Acme Holdings` and exactly one `MasterRenamed` is published

**AC-02 – A status change is applied and published**
Given an `ACTIVE` Master
When `UpdateMaster` is called with status `INACTIVE`
Then the persisted status is `INACTIVE` and exactly one `MasterDeactivated` is published

**AC-03 – Both fields change in one call**
Given an `ACTIVE` Master named `Acme`
When `UpdateMaster` is called with a new name and status `INACTIVE`
Then both are applied and both events are published

**AC-04 – An unchanged value publishes no event**
Given an `ACTIVE` Master named `Acme`
When `UpdateMaster` is called with the name `Acme` and status `ACTIVE`
Then the call succeeds, the Master is unchanged, and **no event is published**

**AC-05 – An empty update is rejected**
Given a Master exists
When `UpdateMaster` is called with neither name nor status
Then `InvalidMasterException` is raised and nothing is persisted

**AC-06 – An unknown id is not found**
Given no Master exists with a given id
When `UpdateMaster` is called with it
Then `MasterNotFoundException` is raised

**AC-07 – An invalid name is rejected and nothing is written**
Given a Master exists
When `UpdateMaster` is called with a blank name
Then `InvalidMasterException` is raised and the Master's stored name is unchanged

AC-07 asserts the rollback, not only the exception. An aggregate mutated in memory before a
later validation fails is the classic way a "rejected" request still writes.


## 8. Failure Scenarios

- Master not found (AC-06).
- Invalid name (AC-07).
- Neither field supplied (AC-05).
- `masterId` not a UUID.
- Repository failure — transaction rolls back.


## 9. API Contract

### REST

Not applicable — `adr/0020-graphql-as-the-only-transport.adr.md`.

### GraphQL

Operation:
```graphql
mutation updateMaster(input: UpdateMasterInput!): UpdateMasterPayload!
```

```graphql
input UpdateMasterInput {
    masterId: ID!
    name: String
    status: MasterStatus
}

type UpdateMasterPayload {
    masterId: ID!
    name: String!
    customerNumber: String!
    status: MasterStatus!
    createdAt: String!
}
```

`name` and `status` are nullable in the input **because they are optional**, which is the one
place GraphQL's nullability and this API's semantics coincide: absent means "leave it alone".

Error classification:
- `BAD_REQUEST` – `InvalidMasterException`, or an empty update, or a malformed id
- `NOT_FOUND` – `MasterNotFoundException`
- `INTERNAL_ERROR` – persistence failure

**Here the vocabulary asymmetry bites.** Over HTTP, "invalid name" is `400` and "nothing to
update" is arguably `422`; both collapse into `BAD_REQUEST`. A client distinguishing them must
read the error message, which is not a contract. If that distinction ever matters, it needs an
error-code field in the payload — not a second classification.

### Executable requests

`api/uc03-update-master.graphql` covers the happy path and one request per classification.


## 10. Definition of Done

Citations name test **classes** rather than methods, for the reason set out in
`uc01-create-master.spec.md` § 10.

### Behaviour
- [ ] AC-01 covered by `UpdateMasterDriverTest`
- [ ] AC-02 covered by `UpdateMasterDriverTest`
- [ ] AC-03 covered by `UpdateMasterDriverTest`
- [ ] AC-04 covered by `MasterTest` — the no-op transitions record no event
- [ ] AC-05 covered by `UpdateMasterDriverTest`
- [ ] AC-06 covered by `UpdateMasterDriverTest`
- [ ] AC-07 covered by `UpdateMasterDriverTest` — asserts the stored value is unchanged
- [ ] `rename`, `activate` and `deactivate` and their conditional event emission covered by
      `MasterTest`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `api/uc03-update-master.http` — not applicable, § 9 says REST is not
- [ ] `api/uc03-update-master.graphql` covers every classification in § 9
- [ ] Persistence roundtrip of an updated Master covered by `MasterJpaRepositoryIT`
- [ ] `documentation/ports/update-master.inport.spec.md` reflects the port as implemented

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` on the architecture axis
- [ ] `conformance-reviewer` matches every § 7 criterion to a test and every § 10 box to an artifact
- [ ] Quality gates green (`test.definition.md` § 7)
