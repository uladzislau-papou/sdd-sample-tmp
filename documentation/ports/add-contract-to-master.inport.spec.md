# Port Specification – AddContractToMasterUseCase (Inport)

## Purpose

Add one Contract to an existing Master.

SDD: see `documentation/use-cases/uc05-add-contract-to-master.spec.md` and
`documentation/domain/aggregate-master.spec.md`.


## 1. Interface

```
contract.core.inport.usecase.AddContractToMasterUseCase
```

An interface, in the context's published API. It is the only way into this use case from
outside the core, and it knows nothing about the transport that reaches it
(`coding-style.definition.md` § 3.3).


## 2. Method Contract

```
fun addContractToMaster(command: AddContractToMasterCommand): AddContractToMasterResult
```

**Command fields:** `masterId: String, contractNumber: String, startDate: LocalDate, endDate: LocalDate,
monthlyAmount: BigDecimal, currency: String`

**Result fields:** masterId, contractId, contractNumber, contractCount

**Exceptions:**
- `MasterNotFoundException` — unknown Master.
- `MasterNotActiveException` — I-09.
- `DuplicateContractNumberException` — I-07.
- `ContractLimitExceededException` — I-08.
- `InvalidContractException` — I-03, I-04, I-05 or I-06.

**Transactional behaviour:** Writes. One transaction. Publishes `ContractAddedToMaster`.

**Idempotency:** none is claimed. The service has no optimistic locking
(`project.definition.md`, Non-Goals) and no request-deduplication key, so a repeated call is a
second call.


## 3. Notes

**The exception precedence is part of this contract**, not an implementation detail:
I-09, then I-07, then I-08. A request violating all three raises `MasterNotActiveException`.
Without a stated order two correct-looking implementations disagree, and the test pinning it
down gets written after a client complains.

The dates are `LocalDate`, which is why they may be supplied by a client at all: § 8.1's
subject is a claim about *our clock*, and a contract's term is business data the parties
agreed. `TimestampRulesTest` would catch them if they were `Instant`.


## 4. Reference Implementation

Class: `contract.inbound.driver.AddContractToMasterDriver`, wired by `bootstrap.ContractConfig`.

`ClassRoleRulesTest.useCaseImplementationsAreDrivers` enforces that a `*UseCase` is implemented
by a driver and nothing else — an adapter implementing it directly would skip the transaction
boundary the driver owns.
