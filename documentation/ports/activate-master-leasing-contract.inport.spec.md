# Port Specification – ActivateMasterLeasingContractUseCase (Inport)

## Purpose

The inbound application boundary for UC02 – ActivateMasterLeasingContract.

SDD: See `documentation/use-cases/uc02-activate-master-leasing-contract.spec.md`


## 1. Interface

```
masterleasing.core.inport.usecase.ActivateMasterLeasingContractUseCase
```

```kotlin
fun activate(
    command: ActivateMasterLeasingContractCommand,
): ActivateMasterLeasingContractResult
```


## 2. Method Contract

### 2.1 Input: ActivateMasterLeasingContractCommand

| Field | Type | Constraint |
|---|---|---|
| `masterLeasingContractId` | `String` | non-blank, must parse as a UUID |
| `activationDate` | `LocalDate` | required; **not** validated against the clock |

**`activationDate` is an input and `activatedAt` is not**, and the distinction is
the one `architecture.definition.md` § 8.1 draws between an agreed business value
and an observation.

`activationDate` is a commercial term: routinely backdated to the start of a month,
occasionally forward-dated to a known start. The parties agreed it, so the caller
supplies it. `activatedAt` — the moment the system recorded the activation — is an
observation and comes from `ClockPort`. **The command has no field for it.**

Deliberately unvalidated: that `activationDate` is not in the future, or not far in
the past. Both are legitimate, and an arbitrary window is worse than none because it
fails for the first real case that exceeds it.


### 2.2 Output: ActivateMasterLeasingContractResult

| Field | Type | Description |
|---|---|---|
| `masterLeasingContractId` | `String` | |
| `status` | `String` | always `"ACTIVE"` on success |
| `activationDate` | `LocalDate` | echoed back |

### 2.3 Exceptions

| Exception | Condition | Classification |
|---|---|---|
| `MasterLeasingContractNotFoundException` | no contract with the given id | `NOT_FOUND` |
| `InvalidMasterLeasingContractStateException` | status is not `DRAFT` (I-04), including a second activation | `CONFLICT` |
| `IllegalArgumentException` | malformed id | `BAD_REQUEST` |

**A second activation throws; it is not an idempotent no-op.** This is the opposite
of `ActivateIndividualLeasingContractsUseCase`, and the discriminator is the
trigger: this is an operator action through a mutation, so a repeat is a mistake the
client should hear about. The individual-lease side is driven by a re-deliverable
event and must tolerate redelivery.


## 3. Transaction Boundary

Owned by the driver. One aggregate, one transaction.

**The fan-out this use case starts is outside that transaction.**
`MasterLeasingContractActivated` is published after commit, and
`individualleasing` activates its pending leases in a separate `REQUIRES_NEW`
transaction (UC06). This use case does not know whether that succeeded and does not
wait for it — which is the accepted trade explained in UC02 § 6.


## 4. Implementation

`masterleasing.inbound.driver.ActivateMasterLeasingContractDriver`.


## 5. Constraints

- The interface MUST remain framework-free.
- The command MUST NOT gain an `activatedAt` field (§ 2.1).
- `MasterLeasingContractActivated` MUST be published into `shared.domain.event`, not
  into this context's event package — it is consumed by another context
  (`modelling.definition.md`, Domain Event).


## 6. Known Gaps

- **Delivery of the activation event is not guaranteed** (ADR 0002). A JVM death
  between commit and delivery leaves leases pending under an active contract with
  nothing to retry. Recoverable and invisible, which is why activation was put on
  the event path and cancellation was not.
- **No way to correct an activation date** once set. Amending it would be a new use
  case; today the only route is a cancellation, which is not equivalent.
