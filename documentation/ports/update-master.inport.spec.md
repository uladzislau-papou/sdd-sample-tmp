# Port Specification – UpdateMasterUseCase (Inport)

## Purpose

Change a Master's name, its status, or both.

SDD: see `documentation/use-cases/uc03-update-master.spec.md` and
`documentation/domain/aggregate-master.spec.md`.


## 1. Interface

```
contract.core.inport.usecase.UpdateMasterUseCase
```

An interface, in the context's published API. It is the only way into this use case from
outside the core, and it knows nothing about the transport that reaches it
(`coding-style.definition.md` § 3.3).


## 2. Method Contract

```
fun updateMaster(command: UpdateMasterCommand): UpdateMasterResult
```

**Command fields:** `masterId: String, name: String?, status: MasterStatus?`

**Result fields:** the Master's fields after the change

**Exceptions:**
- `MasterNotFoundException` — unknown id.
- `InvalidMasterException` — I-01 violated, or neither optional field supplied.

**Transactional behaviour:** Writes. One transaction. Publishes `MasterRenamed`, `MasterActivated` or
`MasterDeactivated` — **only for fields that actually changed**.

**Idempotency:** none is claimed. The service has no optimistic locking
(`project.definition.md`, Non-Goals) and no request-deduplication key, so a repeated call is a
second call.


## 3. Notes

Both optional fields are nullable, and null means "leave it alone" rather than "clear it":
neither field is nullable in the domain, so there is no empty value to set. A command with
both absent is rejected rather than treated as a successful no-op.


## 4. Reference Implementation

Class: `contract.inbound.driver.UpdateMasterDriver`, wired by `bootstrap.ContractConfig`.

`ClassRoleRulesTest.useCaseImplementationsAreDrivers` enforces that a `*UseCase` is implemented
by a driver and nothing else — an adapter implementing it directly would skip the transaction
boundary the driver owns.
