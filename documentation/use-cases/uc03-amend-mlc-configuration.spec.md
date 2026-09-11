# Use Case Specification – AmendMlcConfiguration

## Status
SPECIFIED

## Bounded Context
`masterleasing` — triggered via GraphQL mutation by an external client.

## Purpose

Record a new version of a **LRV**'s commercial terms when they are renegotiated.


## 1. Intent

The terms of a framework agreement change while it lives: the credit limit is
raised, the eligible headcount grows, the price band widens, the notice period is
renegotiated. `MLC_CONFIGURATION.version` exists because the business needs to know
which terms applied when.

This use case produces the next version and makes it current.


## 2. Input Contract

Fields:
- `masterLeasingContractId` (ID) — required
- `expectedCurrentVersion` (Int) — required; the version the caller believes is current
- `configuration` — the full new `MlcConfigurationInput`, same shape as UC01

Validation rules:
- The new version is `expectedCurrentVersion + 1`, computed by the driver, and the
  aggregate rejects it if that is not one past its actual current version (I-05)
- All the value-object invariants of UC01 § 2 apply unchanged

**The amendment carries the full configuration, not a patch.** A partial update
would make "what did version 3 say about the notice period" unanswerable without
replaying every prior version, which is precisely the question versioning exists to
answer. Each version is a complete statement of the terms.

**`expectedCurrentVersion` is the caller's optimistic-concurrency token.** It is the
closest thing this system has to a `@Version` column and it is deliberately weaker:
it turns "two operators amended concurrently" into a failure for the second one
*if* the first already committed, and does nothing under a race. See § 8.


## 3. Output Contract

Return type:
- `masterLeasingContractId` (ID)
- `configurationVersion` (Int) — the new current version

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `MasterLeasingContractNotFoundException` | no contract with the given id | `NOT_FOUND` |
| `InvalidMasterLeasingContractStateException` | status is `CANCELLED` or `ENDED` (I-06) | `CONFLICT` |
| `InvalidConfigurationVersionException` | the new version is not exactly current + 1 (I-05) | `CONFLICT` |
| `InvalidMasterLeasingContractException` | any configuration value-object invariant violated | `BAD_REQUEST` |
| `IllegalArgumentException` | `Money`/`Percentage` invariant, or malformed id | `BAD_REQUEST` |

`InvalidConfigurationVersionException` classifies as `CONFLICT` rather than
`BAD_REQUEST` deliberately: the request was well-formed and became wrong because the
world moved. A client that gets `CONFLICT` should re-read and retry; one that gets
`BAD_REQUEST` should not. Collapsing the two would make the correct client behaviour
unguessable.


## 4. Preconditions

- The contract exists
- Its status is `DRAFT` or `ACTIVE`
- `expectedCurrentVersion` equals the contract's actual current version

**Amending a `DRAFT` contract is permitted and is the common case.** Terms are
corrected repeatedly before signature. Amending an `ACTIVE` contract is also
permitted — that is a genuine renegotiation — and is the reason § 6 has something to
say about leases.


## 5. Flow

1. Parse `MasterLeasingContractId`
2. Build the new `MlcConfiguration` at version `expectedCurrentVersion + 1`, with
   every value object validated
3. Read `now` from `ClockPort`
4. Load via `MasterLeasingContractRepository.findById(...)` → throw
   `MasterLeasingContractNotFoundException` if absent
5. `contract.amendConfiguration(newConfiguration, now)` → throws on I-05 or I-06
6. Persist via `MasterLeasingContractRepository.update(...)`
7. Publish `MlcConfigurationAmended` via `DomainEventPublisher`
8. Return `{ masterLeasingContractId, configurationVersion }`

Ordering note: **step 2 precedes step 4**, so a malformed configuration is a
`BAD_REQUEST` without a database round trip, and the `BAD_REQUEST`/`NOT_FOUND`
distinction never depends on which check happened to run first.


## 6. Side Effects

- Persistence: the `mlc_configuration` row for this contract is **replaced**, and
  `master_leasing_contract.mlc_config_id` points at the new version
- Event publication: `MlcConfigurationAmended`, after commit, context-local — no
  other context consumes it

### An amendment does not reach leases already issued

Only `COPIED_ONCE` inheritance is implemented, so a lease issued under version 2
keeps version 2's terms when the contract moves to version 3
(`documentation/notes.md`).

That is the correct behaviour for `COPIED_ONCE` and it is **not** a considered answer
for `LIVE_LINKED`, which the data model also allows. Implementing `LIVE_LINKED` would
make this use case fan out across every live-linked lease, which is a third
cross-context interaction and needs the event-versus-shared-transaction decision made
explicitly (`architecture.definition.md` § 10) — an ADR, not an increment.

**Superseded versions are not retained.** The previous configuration row is replaced
rather than archived, so `MlcConfigurationAmended` is the only record that version
*n* ever existed, and it carries only the version number. An audit view answering
"what were the terms in March" needs a history table, and that is a schema change
with its own use case. Recorded as open in the aggregate spec § 7.


## 7. Acceptance Criteria

**AC-01 – Happy Path from ACTIVE**
Given an `ACTIVE` contract at configuration version 2
When AmendMlcConfiguration is executed with `expectedCurrentVersion = 2`
Then the current configuration becomes version 3 with the supplied terms
And `MlcConfigurationAmended` is published after commit carrying version 3

**AC-02 – Amending a DRAFT Contract**
Given a `DRAFT` contract at version 1
When AmendMlcConfiguration is executed with `expectedCurrentVersion = 1`
Then the current configuration becomes version 2 and the status stays `DRAFT`

**AC-03 – Stale Expected Version**
Given a contract at version 3
When AmendMlcConfiguration is executed with `expectedCurrentVersion = 1`
Then `InvalidConfigurationVersionException` is raised with classification `CONFLICT`
And the current configuration is unchanged

**AC-04 – Version Gap Rejected**
Given a contract at version 2
When an amendment is attempted at version 5
Then `InvalidConfigurationVersionException` is raised and the configuration is unchanged

**AC-05 – Cancelled Contract**
Given a `CANCELLED` contract
When AmendMlcConfiguration is executed
Then `InvalidMasterLeasingContractStateException` is raised with classification `CONFLICT`

**AC-06 – Terms Are Replaced Wholesale**
Given an `ACTIVE` contract whose version 2 had `eligibleEmployees = 50`
When an amendment supplies a configuration with `eligibleEmployees = 80` and a
  different credit limit
Then version 3 carries both new values, and no value is inherited from version 2

**AC-07 – Invalid Terms Rejected Before Loading**
Given an amendment whose price band is inverted, for a contract id that does not exist
When AmendMlcConfiguration is executed
Then the error is `BAD_REQUEST`, not `NOT_FOUND`


## 8. Failure Scenarios

| Scenario | Exception | Classification |
|----------|-----------|----------------|
| Contract does not exist | `MasterLeasingContractNotFoundException` | `NOT_FOUND` |
| Contract `CANCELLED` or `ENDED` | `InvalidMasterLeasingContractStateException` | `CONFLICT` |
| Expected version stale, or a gap | `InvalidConfigurationVersionException` | `CONFLICT` |
| Configuration value-object invariant violated | `InvalidMasterLeasingContractException` | `BAD_REQUEST` |
| `Money` / `Percentage` invariant, malformed id | `IllegalArgumentException` | `BAD_REQUEST` |

**Known gap: this is not real optimistic concurrency.** Two operators reading
version 2 and both amending will both compute version 3. Under read-committed
isolation the second `update` overwrites the first, and I-05 never fires because the
second transaction read the pre-amendment state. The lost amendment is silent.

`expectedCurrentVersion` narrows the window — it catches the common case where one
operator's page is minutes stale — and does not close it. The fix is a `@Version`
column, which is a persistence-strategy change and therefore an ADR
(`sdd.playbook.md` § 6 item 4). Recorded rather than papered over, because a check
that looks like concurrency control and is not is worse than none.


## 9. GraphQL Contract

Schema (`src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls`):

```graphql
input AmendMlcConfigurationInput {
  masterLeasingContractId: ID!
  expectedCurrentVersion: Int!
  configuration: MlcConfigurationInput!
}

type AmendMlcConfigurationPayload {
  masterLeasingContractId: ID!
  configurationVersion: Int!
}

extend type Mutation {
  amendMlcConfiguration(input: AmendMlcConfigurationInput!): AmendMlcConfigurationPayload!
}
```

`MlcConfigurationInput` is reused from UC01 unchanged — one input type for one
concept. It still has no `version` field; the version is derived from
`expectedCurrentVersion`, never supplied directly.

Operation:
```graphql
mutation AmendMlcConfiguration($input: AmendMlcConfigurationInput!) {
  amendMlcConfiguration(input: $input) {
    masterLeasingContractId
    configurationVersion
  }
}
```

Response:
```json
{
  "data": {
    "amendMlcConfiguration": {
      "masterLeasingContractId": "3f1c...",
      "configurationVersion": 3
    }
  }
}
```

Error classification mapping:
- success – configuration amended
- `NOT_FOUND` – no contract with that id
- `CONFLICT` – stale expected version, version gap, or contract not amendable
- `BAD_REQUEST` – invalid configuration values, malformed id


## 10. Definition of Done

### Behaviour
- [ ] AC-01 covered by `MasterLeasingContractTest.amendConfiguration_replacesCurrentConfiguration`
      and `AmendMlcConfigurationDriverTest.amend_publishesAmendedEvent`
- [ ] AC-02 covered by `MasterLeasingContractTest.amendConfiguration_isPermittedFromDraft`
- [ ] AC-03 covered by `MasterLeasingContractTest.amendConfiguration_throwsInvalidConfigurationVersionException_whenVersionIsStale`
      and `MasterLeasingContractGraphQLControllerTest.amend_returnsConflict_whenVersionStale`
- [ ] AC-04 covered by `MasterLeasingContractTest.amendConfiguration_throwsInvalidConfigurationVersionException_whenVersionSkipsAhead`
- [ ] AC-05 covered by `MasterLeasingContractTest.amendConfiguration_throwsInvalidMasterLeasingContractStateException_whenCancelled`
- [ ] AC-06 covered by `MasterLeasingContractTest.amendConfiguration_carriesEverySuppliedValue_andInheritsNone`
- [ ] AC-07 covered by `AmendMlcConfigurationDriverTest.amend_validatesConfigurationBeforeLoadingTheAggregate`
- [ ] Driver orchestration and event emission covered by `AmendMlcConfigurationDriverTest`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls` declares `amendMlcConfiguration`
- [ ] `graphql/uc03-amend-mlc-configuration.graphql` covers success, `NOT_FOUND`, `CONFLICT` (both causes) and `BAD_REQUEST`
- [ ] Configuration replacement round-trips — covered by
      `MasterLeasingContractPersistenceAdapterIT.update_replacesConfiguration_andBumpsVersion`
- [ ] `documentation/ports/amend-mlc-configuration.inport.spec.md` reflects the inport
- [ ] The lost-update gap in § 8 is recorded in
      `documentation/domain/aggregate-master-leasing-contract.spec.md` § 7 Open

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
