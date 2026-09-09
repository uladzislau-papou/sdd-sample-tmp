# Port Specification – MasterRepository (Outport)

## Purpose

Loads and stores the `Master` aggregate, contracts included.

This is the **only** repository in the service. There is deliberately no
`ContractRepository`: `Contract` is an entity inside the `Master` aggregate, so it has no
independent identity to look up and no transaction boundary of its own
(`adr/0024-one-context-with-master-as-the-aggregate-root.adr.md`). Adding one is drift, not
convenience, and `ddd-hex-reviewer` should report it as such.

It also serves the **read** side. `adr/0025-reads-go-through-the-repository-outport.adr.md`
decided against a separate query port, and named the three conditions that reopen the
question.

SDD: see `documentation/domain/aggregate-master.spec.md` and
`documentation/architecture.definition.md` § 4.6.


## 1. Interface

```
contract.core.outport.MasterRepository
```

Lives in the context's `core.outport`, not in `shared.outport`: it names a
context-specific aggregate, and putting it in the shared kernel would make the shared kernel
depend on a bounded context (`architecture.definition.md` § 11 rule 4).

The interface is expressed entirely in the core's vocabulary — `Master`, `MasterId`. It
returns no `Optional<MasterJpaEntity>`, no Spring Data `Page`, and no persistence type of any
kind, which `ClassRoleRulesTest.repositoryOutportsExposeNoPersistenceType` enforces.


## 2. Method Contract

### 2.1 save

```
fun save(master: Master): Master
```

**Responsibility:** Persist the aggregate — the Master row and every contract it currently
holds — and return the persisted state.

**Preconditions:** `master` is a valid aggregate. The port performs no validation; an
Always-Valid aggregate cannot be constructed in an invalid state, so there is nothing left
to check here.

**Postconditions:**
- The Master is stored, inserted or updated by identity.
- Contracts added since the last load are inserted; contracts removed are deleted. The
  aggregate is stored **as a whole**, so the caller does not sequence contract writes.
- The returned instance carries no pending domain events. Events are pulled and published by
  the driver (`adr/0002`), not by this port.

**Exceptions:** infrastructure failures propagate. The port declares no domain exception —
`DuplicateContractNumberException` and its siblings are raised by the aggregate before this
is ever called.

**Not idempotent in the HTTP sense, and idempotent in the practical one:** saving the same
aggregate twice leaves the same state. There is no optimistic locking
(`project.definition.md`, Non-Goals), so a concurrent save is last-write-wins.

### 2.2 findById

```
fun findById(id: MasterId): Master?
```

**Responsibility:** Load the aggregate by identity, **with its contracts**.

**Preconditions:** none.

**Postconditions:**
- Returns the Master with every contract it holds, or `null` if none exists.
- The returned aggregate has **no pending domain events**: it is rebuilt through
  `Master.reconstitute`, which records nothing.
- Contracts are loaded in the same round trip. Returning a Master whose contracts load lazily
  on access would make the aggregate boundary a lie outside the transaction, and would make
  `adr/0025`'s argument — that there is no N+1 to design around — false.

**Exceptions:** infrastructure failures propagate. **Absence is not an exception.** `null` is
returned and the *driver* raises `MasterNotFoundException`, because whether absence is an
error depends on the use case rather than on the store. Every current use case treats it as
one; that is still the driver's judgement to make.

### 2.3 delete

```
fun delete(master: Master)
```

**Responsibility:** Remove the aggregate and every contract it holds.

**Preconditions:** the Master exists.

**Postconditions:**
- The Master row and all its contract rows are gone.
- No contract row survives referencing the deleted Master. This is the aggregate boundary
  expressed in storage, not a configurable cascade (`uc04-delete-master.spec.md` § 1).

**Exceptions:** infrastructure failures propagate.

**Takes the aggregate, not the id.** UC04 must load the Master anyway — to count its contracts
and to construct `MasterDeleted` — and a `deleteById` would offer a second path that skips the
load and silently cannot do either.


## 3. Usage Context

One transaction per use case, owned by the driver (`architecture.definition.md` § 4.4). The
port is called inside it; it never opens one of its own.

| Use case | Calls |
|----------|-------|
| UC01 CreateMaster | `save` |
| UC02 GetMaster | `findById` (read-only transaction) |
| UC03 UpdateMaster | `findById`, `save` |
| UC04 DeleteMaster | `findById`, `delete` |
| UC05 AddContractToMaster | `findById`, `save` |
| UC06 RemoveContractFromMaster | `findById`, `save` |

There is no `existsById`. A caller that needs to know whether a Master exists needs the Master
— every use case above that checks existence goes on to use it.


## 4. Reference Implementation

Class: `contract.outbound.persistence.MasterJpaRepository`, wired by `bootstrap.ContractConfig`.

It delegates to a Spring Data `MasterJpaEntityRepository` and maps through `MasterMapper`. The
Spring Data interface and the JPA entities stay inside `outbound.persistence`; only this port
crosses into the core, which is what `DependencyRulesTest.rule5_outboundIsReferencedOnlyByBootstrap`
and `rule6_noPersistenceTypeInTheCore` enforce between them.

Mapping back into the domain uses `Master.reconstitute`, which re-checks no invariants and may
be called only from `..outbound.persistence..`
(`ClassRoleRulesTest.reconstituteIsCalledOnlyByPersistence`).

The JPA entities hold the persistence annotations; the domain holds none
(`adr/0011-persistence-annotations-stay-out-of-the-domain.adr.md`). Contracts are mapped as a
child collection cascaded from the Master, and the foreign key carries `ON DELETE CASCADE` so
that the database agrees with the mapping rather than merely being trusted to.
