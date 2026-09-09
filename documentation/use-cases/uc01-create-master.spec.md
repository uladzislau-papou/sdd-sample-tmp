# Use Case Specification – CreateMaster

## Status
SPECIFIED

## Bounded Context
`contract` — triggered via GraphQL by external client.

`contract` is decided by `adr/0024-one-context-with-master-as-the-aggregate-root.adr.md` and
is registered as a `Bounded Context` row in `architecture.definition.md` § 11, citing that
same ADR.

## Purpose

Create a Master with no contracts, in `ACTIVE` status, and return its identity.


## 1. Intent

**Source:** `n/a — invented scope. See `project.definition.md`, "Purpose of the service".`

There is no ticket and no Confluence page behind this specification, and that is deliberate
rather than missing: the domain is a specimen for exercising the method, and every rule in it
is chosen rather than discovered. The conformance axis of `/code-review` therefore cannot
follow this field back to an original request; it compares the code against this spec and
against `documentation/domain/aggregate-master.spec.md`, and that limitation is real.

**Business outcome:** a Master exists and can hold contracts.


## 2. Input Contract

Fields:
- `name`
- `customerNumber`

Validation rules:
- Both are required. Neither may be null or absent.
- `name` — I-01 in `aggregate-master.spec.md` § 2.
- `customerNumber` — I-02.

The invariants are **referenced, not restated**. `aggregate-master.spec.md` § 2 is the single
source; a rule copied here is a rule with two places to drift.

Not accepted on input:
- `id` — assigned by the driver.
- `status` — always `ACTIVE` on creation (§ 3 of the domain spec). A caller wanting an
  inactive Master creates one and calls UC03.
- `createdAt` — read from `ClockPort` by the driver. § 8.1 of `architecture.definition.md`
  forbids a GraphQL input type from carrying a timestamp, and
  `TimestampRulesTest.noTimestampFieldOnExternalTransportInputTypes` enforces it.
- Contracts — a Master is created empty. UC05 adds contracts.


## 3. Output Contract

Return type:
- `CreateMasterResult` carrying `masterId`, `name`, `customerNumber`, `status`, `createdAt`.

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `InvalidMasterException` | I-01 or I-02 violated | `400` |

The HTTP column is filled because the template's table asks for it. **No REST endpoint
exists** (`adr/0020`); the column records what the status would be if one did, and § 9 gives
the classification that actually applies.


## 4. Preconditions

None. This is the one use case that does not require an existing aggregate.


## 5. Flow

1. Driver generates a `MasterId`.
2. Driver reads `now` from `ClockPort`.
3. Driver calls `Master.create(id, MasterName(name), CustomerNumber(customerNumber), now)`.
4. Driver calls `MasterRepository.save(master)`.
5. Driver publishes the events from `master.pullDomainEvents()` through `DomainEventPublisher`,
   inside the same transaction (`adr/0002`).
6. Driver maps the aggregate to `CreateMasterResult`.

Steps 3–5 are one transaction, owned by the driver.


## 6. Side Effects

- **Persistence:** one new `master` row. No contract rows.
- **External calls:** none.
- **Event publication:** `MasterCreated`.


## 7. Acceptance Criteria

**AC-01 – A valid request creates an active Master**
Given no Master exists with the generated id
When `CreateMaster` is called with a valid name and customer number
Then a Master is persisted with status `ACTIVE`, an empty contract list, `createdAt` equal to
the clock's value, and its id is returned

**AC-02 – Creation emits MasterCreated**
Given a valid request
When `CreateMaster` is called
Then exactly one `MasterCreated` event is published, carrying the new master's id

**AC-03 – An invalid name is rejected**
Given a request whose name is blank, whitespace-only, or longer than 200 characters
When `CreateMaster` is called
Then `InvalidMasterException` is raised and nothing is persisted

**AC-04 – An invalid customer number is rejected**
Given a request whose customer number does not match `^[A-Z]{2}-[0-9]{6}$`
When `CreateMaster` is called
Then `InvalidMasterException` is raised and nothing is persisted

**AC-05 – The customer number is not globally unique**
Given a Master already exists with customer number `DE-000001`
When `CreateMaster` is called with the same customer number
Then a second Master is created successfully

AC-05 asserts an **absence of a rule**, which is unusual and deliberate. Uniqueness is the
first thing a reader assumes about a field named `customerNumber`, and the reason it does not
hold is written in `aggregate-master.spec.md` § 2. Without this criterion, someone adds the
constraint later believing they are fixing an oversight.


## 8. Failure Scenarios

- Invalid name (AC-03).
- Invalid customer number (AC-04).
- Repository failure — the transaction rolls back, nothing is persisted, no event is
  delivered to any consumer.

Not a failure scenario: a duplicate customer number (AC-05).


## 9. API Contract

### REST

Not applicable — `adr/0020-graphql-as-the-only-transport.adr.md`. No REST controller is
written, so `api/uc01-create-master.http` does not exist.

### GraphQL

Operation:
```graphql
mutation createMaster(input: CreateMasterInput!): CreateMasterPayload!
```

```graphql
input CreateMasterInput {
    name: String!
    customerNumber: String!
}

type CreateMasterPayload {
    masterId: ID!
    name: String!
    customerNumber: String!
    status: MasterStatus!
    createdAt: String!
}
```

`createdAt` leaves as an ISO-8601 string. It is an **output**, so § 8.1 does not apply — that
rule governs what a client may *supply*.

Error classification:
- `BAD_REQUEST` – `InvalidMasterException`
- `INTERNAL_ERROR` – persistence failure

GraphQL's error vocabulary is coarser than HTTP's. Nothing collapses here, because this use
case has one failure classification; UC05 is where the asymmetry actually bites.

### Executable requests

`api/uc01-create-master.graphql` covers the happy path and one request per classification
above.


## 10. Definition of Done

**On the form of the citations below.** `test.definition.md` § 9 requires an item to name the
test that satisfies it. `SpecCitationsTest` then checks that every `` `Class.method` ``
citation in `documentation/` names a test that exists — so writing the method names now, before
the tests exist, would fail the gate on a specification-only increment.

The items therefore cite **test classes**, which the gate's regex does not match, and each says
what the test must assert. The implementing session replaces each with a
`` `Class.method` `` citation as it writes the test, and the gate starts checking them from
that moment. This is a known hole in the gate, named here rather than worked around silently.

### Behaviour
- [ ] AC-01 covered by `CreateMasterDriverTest` — asserts the saved aggregate's status,
      empty contract list and `createdAt`
- [ ] AC-02 covered by `CreateMasterDriverTest` — asserts one `MasterCreated` published
- [x] AC-03 covered by `MasterNameTest.blankNameThrows`, `MasterNameTest.whitespaceOnlyNameThrows`,
      `MasterNameTest.nameLongerThanTheLimitThrows`,
      `MasterNameTest.nameLongerThanTheLimitOnlyBeforeTrimmingThrows` — one case per rejection
      reason, plus the raw-vs-trimmed boundary
- [ ] AC-04 covered by `CustomerNumberTest` — one case per rejection reason
- [ ] AC-05 covered by `MasterJpaRepositoryIT` — two Masters with one customer number
- [ ] Domain invariants for `Master` covered by `MasterTest`
- [ ] Driver orchestration and event emission covered by `CreateMasterDriverTest`
- [ ] Every failure scenario in § 8 has a negative test
- [ ] The driver reads time from `ClockPort` and not from `Instant.now()`, covered by
      `CreateMasterDriverTest` with a fixed clock

### Contracts
- [ ] `api/uc01-create-master.http` — not applicable, § 9 says REST is not
- [ ] `api/uc01-create-master.graphql` covers every classification in § 9
- [ ] Persistence roundtrip covered by `MasterJpaRepositoryIT`
- [ ] `documentation/ports/create-master.inport.spec.md` reflects the port as implemented
- [ ] `documentation/ports/master-repository.outport.spec.md` reflects the port as implemented

### Governance
- [x] `architecture.definition.md` § 11 has a `contract` row, landing with the package
      — verified by `ContextRegistryTest.topLevelPackagesMatchTheRegistry`
- [ ] `ContractSliceAllowance` / `ContractSliceTripwireTest` retired and `isFailOnNoMatchingTests`
      restored in `build.gradle.kts`, when `ContractSliceTripwireTest.theContractSliceIsStillMissingAtLeastOneLayer`
      fails, per `adr/0027`
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` on the architecture axis
- [ ] `conformance-reviewer` matches every § 7 criterion to a test and every § 10 box to an artifact
- [ ] Quality gates green (`test.definition.md` § 7)
