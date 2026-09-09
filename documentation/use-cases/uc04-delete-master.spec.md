# Use Case Specification – DeleteMaster

## Status
SPECIFIED

## Bounded Context
`contract` — triggered via GraphQL by external client.

## Purpose

Delete a Master and every Contract it holds.


## 1. Intent

**Source:** `n/a — invented scope. See `project.definition.md`, "Purpose of the service".`

**Business outcome:** a Master that should not exist stops existing.

**This is a hard delete, and the contracts go with it.** Both halves are decisions:

- *Hard*, because `project.definition.md` lists soft delete as a Non-Goal. `status` says
  whether a Master is trading, not whether it exists, and UC03 is how a Master is taken out of
  service without losing it. A service with both a status and a deleted flag has two ways to
  express one idea and no rule about which wins.
- *Cascading*, because `Contract` is an entity inside the `Master` aggregate
  (`adr/0024`). A contract has no existence outside its Master, so there is nothing for an
  orphaned contract row to mean. This is a property of the aggregate boundary, not a separate
  cascade policy — it is not configurable and it is not a flag on the request.

Deleting a Master holding contracts is therefore **permitted**, not refused. The alternative —
requiring the caller to empty a Master first — was considered and rejected: it makes a
two-step client flow to protect an invariant that does not exist, since a contract cannot
outlive its Master by construction.


## 2. Input Contract

Fields:
- `masterId`

Validation rules:
- Required, must parse as a UUID.

No confirmation flag, no `force`, no `cascade: true`. A parameter that must always be set to
one value is a parameter that will be set to that value by every client, and it buys nothing.


## 3. Output Contract

Return type:
- `DeleteMasterResult` carrying `masterId` and `deletedContractCount`.

`deletedContractCount` exists so the caller learns what the cascade actually did. A client that
deletes a Master and finds out later it removed forty contracts had the information available
and was not given it.

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `MasterNotFoundException` | No Master with that id | `404` |

No REST endpoint exists (`adr/0020`).


## 4. Preconditions

- The Master must exist.
- No status precondition: an `ACTIVE` Master can be deleted. Requiring deactivation first
  would be a workflow rule, and there is no workflow here.


## 5. Flow

1. Driver parses `masterId`.
2. Driver calls `MasterRepository.findById`; raises `MasterNotFoundException` if empty.
3. Driver records the contract count from the loaded aggregate.
4. Driver **constructs `MasterDeleted`** from the loaded aggregate.
5. Driver calls `MasterRepository.delete(master)`.
6. Driver publishes `MasterDeleted` through `DomainEventPublisher`, inside the transaction.
7. Driver returns `DeleteMasterResult`.

Steps 2–6 are one transaction.

**Step 4 is the one place in this service where a driver constructs a domain event.**
Everywhere else, events come from `pullDomainEvents()` on the aggregate that recorded them —
but deletion removes the aggregate, so there is no post-deletion object to pull from. This is
stated in `aggregate-master.spec.md` § 5 and repeated here because whoever implements UC04
should not need to have read that file to know it is deliberate. It is the only exception, and
a second one is a design smell to argue about, not a precedent to follow.

**Load-then-delete rather than delete-by-id.** The extra read is what makes steps 3 and 4
possible, and what makes "not found" distinguishable from "deleted nothing".


## 6. Side Effects

- **Persistence:** the `master` row and every `contract` row referencing it are removed. The
  cascade is expressed in the JPA mapping and in the foreign key's `ON DELETE CASCADE`, and
  the integration test asserts the contract rows are actually gone — not that the mapping was
  configured.
- **External calls:** none.
- **Event publication:** `MasterDeleted`. **No `ContractRemovedFromMaster` events** are
  published for the cascaded contracts. Their removal is not an event in the domain; it is
  what deleting the aggregate means. Publishing N+1 events would tell a consumer that N
  removals happened, which is a different story from one deletion.


## 7. Acceptance Criteria

**AC-01 – A Master with no contracts is deleted**
Given a Master exists holding no contracts
When `DeleteMaster` is called with its id
Then the Master no longer exists and `deletedContractCount` is `0`

**AC-02 – A Master's contracts are deleted with it**
Given a Master exists holding three contracts
When `DeleteMaster` is called with its id
Then the Master and all three contracts no longer exist, and `deletedContractCount` is `3`

**AC-03 – Deletion publishes exactly one event**
Given a Master exists holding three contracts
When `DeleteMaster` is called
Then exactly one `MasterDeleted` is published and no `ContractRemovedFromMaster` is published

**AC-04 – An unknown id is not found**
Given no Master exists with a given id
When `DeleteMaster` is called with it
Then `MasterNotFoundException` is raised

**AC-05 – Deletion is not idempotent**
Given a Master was just deleted
When `DeleteMaster` is called again with the same id
Then `MasterNotFoundException` is raised

AC-05 fixes a question every delete API faces and most leave open. A second delete is an
error, not a success: the caller is telling us something it believes about the world that is
false, and a silent success hides a client bug. `NOT_FOUND` from a delete is safe to ignore
for a client that genuinely does not care.

**AC-06 – Another Master's contracts are untouched**
Given two Masters exist, each holding contracts
When one is deleted
Then the other Master and all of its contracts are unaffected

AC-06 looks trivial and is the one that catches a cascade written against the wrong foreign
key.


## 8. Failure Scenarios

- Master not found (AC-04, AC-05).
- `masterId` not a UUID.
- Repository failure — the transaction rolls back and **neither** the Master nor its contracts
  are removed.


## 9. API Contract

### REST

Not applicable — `adr/0020-graphql-as-the-only-transport.adr.md`.

### GraphQL

Operation:
```graphql
mutation deleteMaster(input: DeleteMasterInput!): DeleteMasterPayload!
```

```graphql
input DeleteMasterInput {
    masterId: ID!
}

type DeleteMasterPayload {
    masterId: ID!
    deletedContractCount: Int!
}
```

Error classification:
- `NOT_FOUND` – `MasterNotFoundException`
- `BAD_REQUEST` – `masterId` is not a UUID
- `INTERNAL_ERROR` – persistence failure

### Executable requests

`api/uc04-delete-master.graphql` covers the happy path, the cascade case, and one request per
classification.


## 10. Definition of Done

Citations name test **classes** rather than methods, for the reason set out in
`uc01-create-master.spec.md` § 10.

### Behaviour
- [ ] AC-01 covered by `DeleteMasterDriverTest`
- [ ] AC-02 covered by `MasterJpaRepositoryIT` — asserts the contract rows are gone from the
      database, not that a cascade annotation is present
- [ ] AC-03 covered by `DeleteMasterDriverTest` — asserts exactly one event and its type
- [ ] AC-04 covered by `DeleteMasterDriverTest`
- [ ] AC-05 covered by `DeleteMasterDriverTest`
- [ ] AC-06 covered by `MasterJpaRepositoryIT`
- [ ] Rollback on repository failure leaves both Master and contracts intact, covered by
      `MasterRollbackIT`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `api/uc04-delete-master.http` — not applicable, § 9 says REST is not
- [ ] `api/uc04-delete-master.graphql` covers every classification in § 9
- [ ] `documentation/ports/delete-master.inport.spec.md` reflects the port as implemented
- [ ] `documentation/ports/master-repository.outport.spec.md` reflects `delete` as implemented

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` on the architecture axis
- [ ] `conformance-reviewer` matches every § 7 criterion to a test and every § 10 box to an artifact
- [ ] Quality gates green (`test.definition.md` § 7)
