# Port Specification – MasterLeasingContractRepository (Outport)

## Purpose

Write-side persistence for the `MasterLeasingContract` aggregate. The core owns this
abstraction; `mlc.outbound.persistence` provides the implementation.

SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 6 and
`documentation/domain/aggregate-master-leasing-contract.spec.md`.


## 1. Interface

```
mlc.core.outport.MasterLeasingContractRepository
```

## 2. Method Contract

```
void            save(MasterLeasingContract contract)
MasterLeasingContract? findById(MasterLeasingContractId id)
```

### 2.1 `save`

Persists the contract **and its current configuration** — they are one aggregate, and a save
that persisted the contract while dropping its terms would leave a row no invariant describes.

Atomic. The adapter writes the configuration row first, because the contract row carries
`mlc_config_id` and a database that later gains a foreign key there would reject the other
order.

**Both rows are written in code rather than by a JPA cascade.** A `@OneToOne(cascade = ALL)`
would work, and it would also make the write order, the orphan handling and the flush timing
properties of a Hibernate annotation rather than of code a test can read.

### 2.2 `findById`

Returns the aggregate, or `null` when no contract has that identity.

Returns `null` **also** when the contract row exists but its configuration row does not. A
contract whose terms have vanished is not a contract with absent terms — it is a broken
aggregate, and returning a half-built one would hand the core something no invariant describes.

`findById` exists for the roundtrip test rather than for a use case: this service has no read
side (`project.definition.md`), so without it nothing could verify that what was written can be
read back.

## 3. What the port deliberately does not have

**No `update`.** No use case changes a contract yet. When one arrives, note the lesson in
`tour-booking-repository.outport.spec.md` § 2.3: a partial update once discarded an entire use
case's effect while every test stayed green, because each test asserted only the field it cared
about. An `update` here must write the whole aggregate.

**No `findByEmployerId`.** It would exist only to enforce one-active-contract-per-employer, and
**PD-05** decided against that rule. If PD-05 is overturned, the rule needs a **database
constraint** rather than a port method: `project.definition.md`'s last-write-wins non-goal means
two concurrent creations would each read "no existing contract" and both succeed. A lookup here
would look like enforcement while enforcing nothing.

## 4. Persistence mapping

Two tables plus an element collection, created by
`V3__DDL_create_master_leasing_contract.sql`.

Column names follow the JCM data model page so the model stays recognisable to whoever wrote
it, **even where the domain type is named differently**: `kuv_joint_liability` is
`groupJointLiability` in code, because `coding-style.definition.md` § 4.3 forbids carrying a
German abbreviation into an identifier (**PD-00**). That divergence is exactly the freedom that
having two types rather than one shared type buys.

`servicePackageOptions` is an `@ElementCollection` over
`mlc_configuration_service_package`, not a delimited column: a delimiter would add an invariant
("no tier name contains a comma") that is a persistence concern leaking into the domain, and a
split/join pair can be partially forgotten in a way a join table cannot.

**Reconstitution re-checks no invariant.** The value objects rebuilt on read enforce **PD-10**,
a provisional validation matrix expected to be replaced. Re-validating on read would mean
tightening a rule makes previously written contracts unloadable — and the terms of a signed
agreement must not become unreadable because we changed our mind.

The mechanism is a private primary constructor on every value object, validation in a companion
`invoke`, and a `reconstitute` the mapper calls instead — `ClassRoleRulesTest` guards the latter
by matching any `reconstitute` in `core.domain`. The claim was asserted here **before it was
delivered**; `aggregate-master-leasing-contract.spec.md` § 4 records the correction and why no
existing test could have caught it. `ReconstitutionTest` is what makes it falsifiable now.

One placement note, because it looks arbitrary otherwise: the nullable price-range pairing lives
in `MasterLeasingContractMapper.readPriceRange` and not as a `PriceRange.reconstituteOptional`
factory. A companion helper calling `reconstitute` from inside `core.domain` fails
`ClassRoleRulesTest`, and naming it `reconstituteOptional` would have escaped that rule's
exact-name match and left the read path unguarded.

Column nullability follows PD-01. `MasterLeasingContractJpaRepositoryIT.save_persistsOptionalTermsAsNull_whenAbsent`
is the evidence that a `NOT NULL` added later would break existing rows, which is the asymmetry
PD-01 cites.

## 5. Outbox

Not this port's concern. `adr/0019` has a use case write its state **and** an outbox record in
one transaction, and `adr/0022` puts the record's write inside the `DomainEventPublisher`
adapter rather than here — so the driver calls two ports and both writes land in its
transaction.

**The outbox adapter does not exist yet** (`uc07` § 5's deferral note); the bean wired behind
`DomainEventPublisher` logs. Building it is its own increment, with the record's content
deferred to its own outbound port spec as `adr/0019` directs.

## 6. Tests

`MasterLeasingContractJpaRepositoryIT` — against real PostgreSQL via Testcontainers, with
Flyway imported explicitly so the schema comes from the migrations. Under `ddl-auto: validate`
that makes it the proof that the migration and the entity mappings agree, which carries weight
here: the aggregate has twenty-one persisted columns across two tables plus a collection, and
the failure mode of a wide mapping is one column silently dropped — invisible to any domain or
driver test, because both observe the aggregate in memory. Hence
`save_thenFindById_roundTripsEveryTerm` asserts every field rather than a representative few.
