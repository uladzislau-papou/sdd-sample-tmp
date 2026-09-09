# Port Specification – GetMasterUseCase (Inport)

## Purpose

Return one Master with the contracts it holds.

SDD: see `documentation/use-cases/uc02-get-master.spec.md` and
`documentation/domain/aggregate-master.spec.md`.


## 1. Interface

```
contract.core.inport.usecase.GetMasterUseCase
```

An interface, in the context's published API. It is the only way into this use case from
outside the core, and it knows nothing about the transport that reaches it
(`coding-style.definition.md` § 3.3).


## 2. Method Contract

```
fun getMaster(command: GetMasterQuery): GetMasterResult
```

**Command fields:** `masterId: String`

**Result fields:** the Master's fields plus its contracts, ordered by contract number

**Exceptions:**
- `MasterNotFoundException` — no Master with that id.

**Transactional behaviour:** Reads only. Read-only transaction. Publishes nothing.

**Idempotency:** none is claimed. The service has no optimistic locking
(`project.definition.md`, Non-Goals) and no request-deduplication key, so a repeated call is a
second call.


## 3. Notes

Named `...Query` rather than `...Command` because it changes nothing. It is still an
inbound port and still implemented by a driver — `adr/0025` decided against a separate query
side, so a read is an ordinary use case that happens not to write.


## 4. Reference Implementation

Class: `contract.inbound.driver.GetMasterDriver`, wired by `bootstrap.ContractConfig`.

`ClassRoleRulesTest.useCaseImplementationsAreDrivers` enforces that a `*UseCase` is implemented
by a driver and nothing else — an adapter implementing it directly would skip the transaction
boundary the driver owns.
