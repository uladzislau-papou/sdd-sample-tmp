# Port Specification – CreateMasterUseCase (Inport)

## Purpose

Create a Master with no contracts, in `ACTIVE` status.

SDD: see `documentation/use-cases/uc01-create-master.spec.md` and
`documentation/domain/aggregate-master.spec.md`.


## 1. Interface

```
contract.core.inport.usecase.CreateMasterUseCase
```

An interface, in the context's published API. It is the only way into this use case from
outside the core, and it knows nothing about the transport that reaches it
(`coding-style.definition.md` § 3.3).


## 2. Method Contract

```
fun createMaster(command: CreateMasterCommand): CreateMasterResult
```

**Command fields:** `name: String, customerNumber: String`

**Result fields:** masterId, name, customerNumber, status, createdAt

**Exceptions:**
- `InvalidMasterException` — I-01 or I-02 violated.

**Transactional behaviour:** Writes. One transaction. Publishes `MasterCreated`.

**Idempotency:** none is claimed. The service has no optimistic locking
(`project.definition.md`, Non-Goals) and no request-deduplication key, so a repeated call is a
second call.


## 3. Notes

The command carries **no** `id`, `status` or `createdAt`. The driver assigns the first, the
aggregate fixes the second, and the third comes from `ClockPort` — a client may not supply a
timestamp (`architecture.definition.md` § 8.1).


## 4. Reference Implementation

Class: `contract.inbound.driver.CreateMasterDriver`, wired by `bootstrap.ContractConfig`.

`ClassRoleRulesTest.useCaseImplementationsAreDrivers` enforces that a `*UseCase` is implemented
by a driver and nothing else — an adapter implementing it directly would skip the transaction
boundary the driver owns.
