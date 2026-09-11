# Port Specification – RegisterMasterLeasingContractUseCase (Inport)

## Purpose

The inbound application boundary for UC01 – RegisterMasterLeasingContract. The only
entry point for bringing a **Leasing-Rahmenvertrag (LRV)** into the system.

SDD: See `documentation/use-cases/uc01-register-master-leasing-contract.spec.md`


## 1. Interface

```
masterleasing.core.inport.usecase.RegisterMasterLeasingContractUseCase
```

```kotlin
fun register(
    command: RegisterMasterLeasingContractCommand,
): RegisterMasterLeasingContractResult
```


## 2. Method Contract

### 2.1 Input: RegisterMasterLeasingContractCommand

| Field | Type | Constraint |
|---|---|---|
| `employerId` | `String` | non-blank |
| `lessorId` | `String` | non-blank |
| `partnerNumber` | `String` | non-blank |
| `parentMasterLeasingContractId` | `String?` | when present, a well-formed UUID and not the new contract's own id |
| `configuration` | `MlcConfigurationCommand` | see below |

`MlcConfigurationCommand`:

| Field | Type | Constraint |
|---|---|---|
| `contractType` | `ContractType` | |
| `salesChannel` | `SalesChannel` | |
| `inheritanceMode` | `InheritanceMode` | only `COPIED_ONCE` is implemented |
| `currency` | `String` | ISO-4217 code; applies to every amount below |
| `creditLimitAmount` | `BigDecimal` | `>= 0` |
| `priceRangeMin` | `BigDecimal` | `>= 0`, `<= priceRangeMax` |
| `priceRangeMax` | `BigDecimal` | `>= 0` |
| `eligibleEmployees` | `Int` | `>= 0` |
| `jointLiability` | `Boolean` | |
| `returnQuotaPercentage` | `BigDecimal` | `0..100` |
| `noticePeriodMonths` | `Int` | `1..36` |

**Three fields are absent from this command and their absence is contractual**:

- **no `version`** — the initial configuration is version 1 by definition (I-03).
  Accepting one would let a caller start a contract at version 7 and make the
  amendment sequence meaningless.
- **no `activationDate`** — registering does not activate. That is UC02.
- **no timestamp** — `occurredAt` comes from `ClockPort`
  (`architecture.definition.md` § 8.1).

`BigDecimal` rather than `Double` throughout. The command is framework-free and the
conversion from the GraphQL layer's decimal strings happens in the controller
(`modelling.definition.md`, Money).

The three enums are domain types from `core.domain`, which an inport command may
reference (`architecture.definition.md` § 4.2). They carry no behaviour and no
invariant, so no domain logic crosses with them.

### 2.2 Output: RegisterMasterLeasingContractResult

| Field | Type | Description |
|---|---|---|
| `masterLeasingContractId` | `String` | the generated id, as a string |
| `status` | `String` | always `"DRAFT"` on success |
| `configurationVersion` | `Int` | always `1` on success |

The id is returned as a `String` rather than as `MasterLeasingContractId` because a
result type is a data carrier for adapters, and the GraphQL layer would immediately
unwrap it. `status` is likewise a `String` rather than the enum: the payload is a
transport shape, and exposing the enum would make adding a state a schema concern.

### 2.3 Exceptions

| Exception | Condition | Classification |
|---|---|---|
| `InvalidMasterLeasingContractException` | any creation or value-object invariant (I-01, I-02, I-03, I-09 to I-13) | `BAD_REQUEST` |
| `IllegalArgumentException` | `Money`/`Percentage` invariant, or a malformed parent id | `BAD_REQUEST` |

`IllegalArgumentException` appears because `Money` and `Percentage` live in
`shared.domain` and cannot reference a context's exception type — the shared-kernel
exemption in `coding-style.definition.md` § 6.2.


## 3. Transaction Boundary

Owned by the driver. The GraphQL controller is transaction-unaware.

One aggregate, one transaction. No cross-context call.


## 4. Implementation

`masterleasing.inbound.driver.RegisterMasterLeasingContractDriver`.

It builds every value object **before** constructing the aggregate, so an invalid
credit limit is a `BAD_REQUEST` with no database round trip (UC01 § 5).


## 5. Constraints

- MUST NOT be called from within another transaction boundary.
- The interface MUST remain framework-free.
- Command and result types MUST NOT contain aggregates or value objects — enums are
  permitted (§ 2.1).
- The command MUST NOT gain a `version`, an `activationDate` or a timestamp (§ 2.1).


## 6. Known Gaps

- **No referential check** on `employerId`, `lessorId` or
  `parentMasterLeasingContractId` (UC01 § 8). The first two cannot be checked from
  here; the third is a deliberate omission because checking existence would not
  prevent the cycle that matters.
- **No idempotency key.** A retried registration creates a second contract. The
  caller generates no id, so there is nothing to deduplicate on. If retries become a
  real client concern, an idempotency key on the command is the shape — and it is a
  contract change, not an implementation detail.
