# Port Specification – RemoveContractFromMasterUseCase (Inport)

## Purpose

Remove one Contract from the Master that holds it.

SDD: see `documentation/use-cases/uc06-remove-contract-from-master.spec.md` and
`documentation/domain/aggregate-master.spec.md`.


## 1. Interface

```
contract.core.inport.usecase.RemoveContractFromMasterUseCase
```

An interface, in the context's published API. It is the only way into this use case from
outside the core, and it knows nothing about the transport that reaches it
(`coding-style.definition.md` § 3.3).


## 2. Method Contract

```
fun removeContractFromMaster(command: RemoveContractFromMasterCommand): RemoveContractFromMasterResult
```

**Command fields:** `masterId: String, contractId: String`

**Result fields:** masterId, contractId, contractCount

**Exceptions:**
- `MasterNotFoundException` — unknown Master.
- `ContractNotFoundException` — this Master holds no contract with that id, **including the
  case where another Master holds it**.

**Transactional behaviour:** Writes. One transaction. Publishes `ContractRemovedFromMaster`.

**Idempotency:** none is claimed. The service has no optimistic locking
(`project.definition.md`, Non-Goals) and no request-deduplication key, so a repeated call is a
second call.


## 3. Notes

Both ids are required: a contract is never addressed without its Master, because there is no
repository that could find one without it. Removal is by contract **id**, not by number — the
number is unique within the Master but is mutable in principle, and an API keyed on a mutable
field is one rename away from removing the wrong row.

Unlike UC05 there is **no status precondition**. A contract can be removed from an `INACTIVE`
Master; only adding is gated by I-09.


## 4. Reference Implementation

Class: `contract.inbound.driver.RemoveContractFromMasterDriver`, wired by `bootstrap.ContractConfig`.

`ClassRoleRulesTest.useCaseImplementationsAreDrivers` enforces that a `*UseCase` is implemented
by a driver and nothing else — an adapter implementing it directly would skip the transaction
boundary the driver owns.
