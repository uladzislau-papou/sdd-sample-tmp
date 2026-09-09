# Use Case Specification – GetMaster

## Status
SPECIFIED

## Bounded Context
`contract` — triggered via GraphQL by external client.

## Purpose

Return one Master by its identity, together with the contracts it holds.


## 1. Intent

**Source:** `n/a — invented scope. See `project.definition.md`, "Purpose of the service".`

**Business outcome:** a client can read a Master and see what it holds. This is the only read
in the service, and it is what makes the other five verifiable from outside.

**This use case spends a decision that was owed for the life of the repository.**
`adr/0025-reads-go-through-the-repository-outport.adr.md` was written for it: reads use the
same `MasterRepository` the writes use, with no query port and no projection. Read that ADR
before adding a second read — it names the three conditions under which the answer changes.


## 2. Input Contract

Fields:
- `masterId`

Validation rules:
- Required, and must parse as a UUID.
- A syntactically invalid id is a **bad request**, not a missing Master. The two are different
  failures with different classifications, and collapsing them tells a client with a typo to
  go looking for a record that never existed.


## 3. Output Contract

Return type:
- `GetMasterResult` carrying the Master's `id`, `name`, `customerNumber`, `status`,
  `createdAt`, and a list of its contracts. Each contract carries `id`, `contractNumber`,
  `startDate`, `endDate`, `monthlyAmount` and `currency`.

Contracts are returned **ordered by `contractNumber` ascending**. An unordered collection
returned over an API becomes ordered by accident of insertion, and a client will come to rely
on it; stating the order makes it testable.

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `MasterNotFoundException` | No Master with that id | `404` |
| `IllegalArgumentException` | `masterId` is not a UUID | `400` |

No REST endpoint exists (`adr/0020`); the column records what the status would be.


## 4. Preconditions

- The Master must exist.


## 5. Flow

1. Driver parses `masterId` into a `MasterId`.
2. Driver calls `MasterRepository.findById(masterId)`.
3. If nothing is returned, driver raises `MasterNotFoundException`.
4. Driver maps the aggregate — including its contracts, sorted by contract number — to
   `GetMasterResult`.

Read-only. The driver's transaction is `readOnly = true`; no events are published, because
nothing happened.


## 6. Side Effects

- **Persistence:** none. This is the only use case that writes nothing.
- **External calls:** none.
- **Event publication:** none. Reading is not a domain event, and a `MasterViewed` event would
  be an audit feature that `project.definition.md` lists as a Non-Goal.


## 7. Acceptance Criteria

**AC-01 – An existing Master is returned with its contracts**
Given a Master exists holding three contracts
When `GetMaster` is called with its id
Then the Master's fields and all three contracts are returned

**AC-02 – Contracts are ordered by contract number**
Given a Master holds contracts added in the order `C-0003`, `C-0001`, `C-0002`
When `GetMaster` is called
Then the contracts are returned in the order `C-0001`, `C-0002`, `C-0003`

**AC-03 – A Master with no contracts returns an empty list**
Given a Master exists holding no contracts
When `GetMaster` is called
Then the Master is returned with an empty contract list, not an error and not null

**AC-04 – An unknown id is not found**
Given no Master exists with a given id
When `GetMaster` is called with it
Then `MasterNotFoundException` is raised

**AC-05 – An inactive Master is still readable**
Given a Master exists with status `INACTIVE`
When `GetMaster` is called
Then it is returned normally, with status `INACTIVE`

AC-05 exists because "inactive" invites a reader to assume filtering. There is no soft delete
here (`project.definition.md`, Non-Goals) and `status` does not hide a record.


## 8. Failure Scenarios

- Master not found (AC-04).
- `masterId` not a UUID.
- Repository failure.


## 9. API Contract

### REST

Not applicable — `adr/0020-graphql-as-the-only-transport.adr.md`.

### GraphQL

Operation:
```graphql
query master(id: ID!): Master
```

```graphql
type Master {
    id: ID!
    name: String!
    customerNumber: String!
    status: MasterStatus!
    createdAt: String!
    contracts: [Contract!]!
}

type Contract {
    id: ID!
    contractNumber: String!
    startDate: String!
    endDate: String!
    monthlyAmount: String!
    currency: String!
}

enum MasterStatus { ACTIVE INACTIVE }
```

`monthlyAmount` is a **String**, not a `Float`. A decimal amount through an IEEE-754 double is
a rounding bug waiting for a large enough value, and GraphQL has no decimal scalar by default.
The client parses it as a decimal.

**This operation replaces `Query.apiVersion`.** That field existed only so the schema had a
legal `Query` root before the service had a read side (`adr/0020` Consequences). It is removed
in the same increment, not kept alongside.

Error classification:
- `NOT_FOUND` – `MasterNotFoundException`
- `BAD_REQUEST` – `masterId` is not a UUID
- `INTERNAL_ERROR` – persistence failure

### Executable requests

`api/uc02-get-master.graphql` covers the happy path and one request per classification.


## 10. Definition of Done

Citations name test **classes** rather than methods, for the reason set out in
`uc01-create-master.spec.md` § 10. The implementing session replaces each with a
`` `Class.method` `` citation as the test is written.

### Behaviour
- [ ] AC-01 covered by `GetMasterDriverTest`
- [ ] AC-02 covered by `GetMasterDriverTest` — asserts order, with contracts added out of order
- [ ] AC-03 covered by `GetMasterDriverTest` — asserts an empty list, not null
- [ ] AC-04 covered by `GetMasterDriverTest` — asserts `MasterNotFoundException`
- [ ] AC-05 covered by `GetMasterDriverTest`
- [ ] Every failure scenario in § 8 has a negative test
- [ ] The driver's transaction is read-only, and no event is published on a read, covered by
      `GetMasterDriverTest`

### Contracts
- [ ] `api/uc02-get-master.http` — not applicable, § 9 says REST is not
- [ ] `api/uc02-get-master.graphql` covers every classification in § 9
- [ ] Loading a Master with its contracts in one round trip covered by `MasterJpaRepositoryIT`
- [ ] `documentation/ports/get-master.inport.spec.md` reflects the port as implemented
- [ ] `documentation/ports/master-repository.outport.spec.md` reflects `findById` as implemented
- [ ] `Query.apiVersion` removed from the GraphQL schema

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` on the architecture axis
- [ ] `conformance-reviewer` matches every § 7 criterion to a test and every § 10 box to an artifact
- [ ] Quality gates green (`test.definition.md` § 7)
