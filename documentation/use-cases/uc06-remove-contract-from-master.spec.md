# Use Case Specification – RemoveContractFromMaster

## Status
SPECIFIED

## Bounded Context
`contract` — triggered via GraphQL by external client.

## Purpose

Remove one Contract from the Master that holds it.


## 1. Intent

**Source:** `n/a — invented scope. See `project.definition.md`, "Purpose of the service".`

**Business outcome:** a Master stops holding a contract it should not hold.

**A contract is never addressed by its id alone.** The request names both the Master and the
contract, and the Master is the aggregate loaded. That is not defensive parameter-passing: it
follows from `Contract` being an entity inside the `Master` aggregate (`adr/0024`), which means
there is no repository that could find a contract without its Master and no transaction
boundary that could contain the change. An API taking only `contractId` would imply an
aggregate root that does not exist.


## 2. Input Contract

Fields:
- `masterId`
- `contractId`

Validation rules:
- Both required, both must parse as UUIDs.

Removal is **by contract id, not by contract number.** The number is unique within the Master
(I-07) so it would work, but it is also the field UC03-style corrections would change, and an
API keyed on a mutable field is one rename away from deleting the wrong row.


## 3. Output Contract

Return type:
- `RemoveContractFromMasterResult` carrying `masterId`, `contractId` and the Master's resulting
  `contractCount`.

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `MasterNotFoundException` | No Master with that id | `404` |
| `ContractNotFoundException` | The Master holds no contract with that id | `404` |

No REST endpoint exists (`adr/0020`).


## 4. Preconditions

- The Master must exist.
- The Master must hold a contract with the given id.
- **No status precondition.** A contract can be removed from an `INACTIVE` Master.

The asymmetry with UC05 is deliberate and is the thing most likely to be "fixed" by mistake.
I-09 gates *adding* only. A Master that has stopped trading must still be able to shed
contracts — a rule that traps data inside a deactivated record is a rule that gets worked
around, usually by reactivating the Master, which is worse than the thing the rule prevented.
`aggregate-master.spec.md` § 4 states it at the aggregate; AC-04 asserts it.


## 5. Flow

1. Driver parses both ids.
2. Driver calls `MasterRepository.findById`; raises `MasterNotFoundException` if empty.
3. Driver calls `master.removeContract(contractId)`, which raises `ContractNotFoundException`
   if this Master holds no such contract.
4. Driver calls `MasterRepository.save(master)`.
5. Driver publishes `master.pullDomainEvents()` inside the transaction.
6. Driver maps to `RemoveContractFromMasterResult`.

Steps 2–5 are one transaction.

**A contract belonging to a different Master is `ContractNotFoundException`, not a
cross-Master removal and not a distinct "wrong owner" error.** From this Master's point of
view the contract does not exist, which is exactly what the exception says. A separate error
would have to reveal that the contract exists somewhere else — information the caller has not
demonstrated any right to.


## 6. Side Effects

- **Persistence:** the `contract` row is removed. The `master` row is unchanged.
- **External calls:** none.
- **Event publication:** `ContractRemovedFromMaster`.


## 7. Acceptance Criteria

**AC-01 – A held contract is removed**
Given an `ACTIVE` Master holding two contracts
When `RemoveContractFromMaster` is called with one of their ids
Then that contract is gone, the other remains, and `contractCount` is `1`

**AC-02 – Removal publishes ContractRemovedFromMaster**
Given a Master holding a contract
When it is removed
Then exactly one `ContractRemovedFromMaster` is published, carrying both ids

**AC-03 – Removing the last contract is permitted**
Given a Master holding exactly one contract
When it is removed
Then the Master remains, holding no contracts, and `contractCount` is `0`

**AC-04 – A contract can be removed from an inactive Master**
Given an `INACTIVE` Master holding a contract
When `RemoveContractFromMaster` is called
Then it succeeds

**AC-05 – A contract this Master does not hold is not found**
Given a Master holding no contract with a given id
When `RemoveContractFromMaster` is called with it
Then `ContractNotFoundException` is raised and nothing is persisted

**AC-06 – A contract belonging to another Master is not found**
Given Master A holds contract X and Master B holds nothing
When `RemoveContractFromMaster` is called with Master B's id and contract X's id
Then `ContractNotFoundException` is raised and **contract X still belongs to Master A**

**AC-07 – An unknown Master is not found**
Given no Master exists with a given id
When `RemoveContractFromMaster` is called with it
Then `MasterNotFoundException` is raised

**AC-08 – The freed contract number becomes reusable**
Given a Master held contract `C-0001` and it was removed
When a new contract with number `C-0001` is added to that Master
Then it succeeds

AC-08 pins down a question I-07 leaves open on its own: whether uniqueness is over *current*
contracts or over every number the Master has ever used. It is over current ones. Stated here
because the alternative — a number burned forever — is a defensible design that this one is
not, and the difference is invisible until someone tries it.


## 8. Failure Scenarios

- Master not found (AC-07).
- Contract not held by this Master (AC-05, AC-06).
- Either id not a UUID.
- Repository failure — transaction rolls back and the contract remains.


## 9. API Contract

### REST

Not applicable — `adr/0020-graphql-as-the-only-transport.adr.md`.

### GraphQL

Operation:
```graphql
mutation removeContractFromMaster(
    input: RemoveContractFromMasterInput!
): RemoveContractFromMasterPayload!
```

```graphql
input RemoveContractFromMasterInput {
    masterId: ID!
    contractId: ID!
}

type RemoveContractFromMasterPayload {
    masterId: ID!
    contractId: ID!
    contractCount: Int!
}
```

Error classification:
- `NOT_FOUND` – `MasterNotFoundException` **and** `ContractNotFoundException`
- `BAD_REQUEST` – either id is not a UUID
- `INTERNAL_ERROR` – persistence failure

Two distinct domain exceptions share `NOT_FOUND` here. Unlike UC05's collapse, this one loses
nothing a client can act on: in both cases the thing the caller named is not there, and the
remedy is the same. The exceptions stay distinct in the domain because the *code* acts on the
difference — one is raised by the driver, one by the aggregate.

### Executable requests

`api/uc06-remove-contract-from-master.graphql` covers the happy path and one request per
condition, including both `NOT_FOUND` causes separately.


## 10. Definition of Done

Citations name test **classes** rather than methods, for the reason set out in
`uc01-create-master.spec.md` § 10.

### Behaviour
- [ ] AC-01 covered by `RemoveContractFromMasterDriverTest`
- [ ] AC-02 covered by `RemoveContractFromMasterDriverTest`
- [ ] AC-03 covered by `MasterTest`
- [ ] AC-04 covered by `MasterTest` — the asymmetry with I-09 asserted, not assumed
- [ ] AC-05 covered by `MasterTest`
- [ ] AC-06 covered by `MasterJpaRepositoryIT` — asserts contract X still belongs to Master A
- [ ] AC-07 covered by `RemoveContractFromMasterDriverTest`
- [ ] AC-08 covered by `MasterTest`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `api/uc06-remove-contract-from-master.http` — not applicable, § 9 says REST is not
- [ ] `api/uc06-remove-contract-from-master.graphql` covers every condition in § 9
- [ ] Removal persisted and the contract row gone, covered by `MasterJpaRepositoryIT`
- [ ] `documentation/ports/remove-contract-from-master.inport.spec.md` reflects the port as
      implemented

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` on the architecture axis
- [ ] `conformance-reviewer` matches every § 7 criterion to a test and every § 10 box to an artifact
- [ ] Quality gates green (`test.definition.md` § 7)
