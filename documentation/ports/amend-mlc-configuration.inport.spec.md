# Port Specification – AmendMlcConfigurationUseCase (Inport)

## Purpose

The inbound application boundary for UC03 – AmendMlcConfiguration: recording a new
version of a **LRV**'s commercial terms.

SDD: See `documentation/use-cases/uc03-amend-mlc-configuration.spec.md`


## 1. Interface

```
masterleasing.core.inport.usecase.AmendMlcConfigurationUseCase
```

```kotlin
fun amend(command: AmendMlcConfigurationCommand): AmendMlcConfigurationResult
```


## 2. Method Contract

### 2.1 Input: AmendMlcConfigurationCommand

| Field | Type | Constraint |
|---|---|---|
| `masterLeasingContractId` | `String` | non-blank, must parse as a UUID |
| `expectedCurrentVersion` | `Int` | `>= 1`; the version the caller believes is current |
| `configuration` | `MlcConfigurationCommand` | the **full** new terms, same type as UC01 |

**The amendment carries the full configuration, not a patch.** A partial update
would make "what did version 3 say about the notice period" unanswerable without
replaying every prior version — precisely the question versioning exists to answer.
Each version is a complete statement of the terms.

**`expectedCurrentVersion` is the caller's concurrency token, and the new version is
derived from it** as `expectedCurrentVersion + 1`. The command carries no explicit
new version, so a caller cannot request a gap. The aggregate then rejects the result
if it is not exactly one past the actual current version (I-05).

`MlcConfigurationCommand` is reused from UC01 unchanged — one input type for one
concept — and still has no `version` field.

### 2.2 Output: AmendMlcConfigurationResult

| Field | Type | Description |
|---|---|---|
| `masterLeasingContractId` | `String` | |
| `configurationVersion` | `Int` | the new current version |

### 2.3 Exceptions

| Exception | Condition | Classification |
|---|---|---|
| `MasterLeasingContractNotFoundException` | no contract with the given id | `NOT_FOUND` |
| `InvalidMasterLeasingContractStateException` | status is `CANCELLED` or `ENDED` (I-06) | `CONFLICT` |
| `InvalidConfigurationVersionException` | the derived version is not exactly current + 1 (I-05) | `CONFLICT` |
| `InvalidMasterLeasingContractException` | any configuration value-object invariant | `BAD_REQUEST` |
| `IllegalArgumentException` | `Money`/`Percentage` invariant, or malformed id | `BAD_REQUEST` |

**`InvalidConfigurationVersionException` classifies as `CONFLICT`, not
`BAD_REQUEST`.** The request was well-formed and became wrong because the world
moved. A client that gets `CONFLICT` should re-read and retry; one that gets
`BAD_REQUEST` should not. Collapsing them would make the correct client behaviour
unguessable, which is the whole reason the two classifications are distinguished at
all (`architecture.definition.md` § 4.5).

It is a distinct exception type rather than a reuse of
`InvalidMasterLeasingContractStateException` for the same reason: both classify as
`CONFLICT`, but one means "this contract cannot be amended" and the other means
"somebody amended it before you". A client retries the second and not the first.


## 3. Transaction Boundary

Owned by the driver. One aggregate, one transaction. No cross-context call — an
amendment does not reach leases already issued, because only `COPIED_ONCE`
inheritance is implemented (`documentation/notes.md`).


## 4. Implementation

`masterleasing.inbound.driver.AmendMlcConfigurationDriver`.

It builds the new `MlcConfiguration` **before** loading the aggregate, so an invalid
configuration is a `BAD_REQUEST` without a database round trip and the
`BAD_REQUEST`/`NOT_FOUND` distinction never depends on which check ran first
(UC03 § 5).


## 5. Constraints

- The interface MUST remain framework-free.
- The command MUST NOT carry an explicit new version (§ 2.1).
- The command MUST NOT become a patch type (§ 2.1).
- The driver MUST NOT propagate the amendment to issued leases while
  `LIVE_LINKED` inheritance is unimplemented — doing so would be a new cross-context
  interaction and an ADR (`sdd.playbook.md` § 6 item 10).


## 6. Known Gaps

- **`expectedCurrentVersion` is not real optimistic concurrency.** Two operators
  reading version 2 both compute version 3; under read-committed isolation the second
  `update` overwrites the first, and I-05 never fires because the second transaction
  read the pre-amendment state. The lost amendment is silent.

  It narrows the window — it catches the common case of a stale page — and does not
  close it. The fix is a `@Version` column, which is a persistence-strategy change
  and therefore an ADR (`sdd.playbook.md` § 6 item 4). Recorded rather than papered
  over, because a check that looks like concurrency control and is not is worse than
  none: it will be trusted.
- **Superseded versions are not retained** (UC03 § 6). `MlcConfigurationAmended` is
  the only record that version *n* existed, and it carries only the number.
