# Port Specification – DeleteMasterUseCase (Inport)

## Purpose

Delete a Master and every Contract it holds.

SDD: see `documentation/use-cases/uc04-delete-master.spec.md` and
`documentation/domain/aggregate-master.spec.md`.


## 1. Interface

```
contract.core.inport.usecase.DeleteMasterUseCase
```

An interface, in the context's published API. It is the only way into this use case from
outside the core, and it knows nothing about the transport that reaches it
(`coding-style.definition.md` § 3.3).


## 2. Method Contract

```
fun deleteMaster(command: DeleteMasterCommand): DeleteMasterResult
```

**Command fields:** `masterId: String`

**Result fields:** masterId, deletedContractCount

**Exceptions:**
- `MasterNotFoundException` — unknown id, including a second delete of the same Master.

**Transactional behaviour:** Writes. One transaction. Publishes `MasterDeleted`, constructed **by the driver** — the
aggregate is gone, so there is nothing left to pull events from. It is the only place in the
service where a driver builds a domain event, and `aggregate-master.spec.md` § 5 says so too.

**Idempotency:** none is claimed. The service has no optimistic locking
(`project.definition.md`, Non-Goals) and no request-deduplication key, so a repeated call is a
second call.


## 3. Notes

`deletedContractCount` is returned so the caller learns what the cascade did. There is no
`force` or `cascade` flag: a contract cannot outlive its Master by construction, so there is
no second behaviour to select.


## 4. Reference Implementation

Class: `contract.inbound.driver.DeleteMasterDriver`, wired by `bootstrap.ContractConfig`.

`ClassRoleRulesTest.useCaseImplementationsAreDrivers` enforces that a `*UseCase` is implemented
by a driver and nothing else — an adapter implementing it directly would skip the transaction
boundary the driver owns.
