# ADR 0025 – Reads Go Through the Repository Outport, Not a Separate Query Side

## Status
Accepted

## Context

This is the decision the repository has owed itself since it was a template. Every version of
it recorded the same debt: `project.definition.md` listed the absence of a read side as a
Non-Goal, `adr/README.md` said the first display use case writes the ADR, and the GraphQL
schema's `Query` root carried one infrastructure field — `apiVersion` — precisely so that
nothing would ship a read side by accident.

The debt is now due. UC02 (`GetMaster`) is a read, and it cannot be specified without saying
where the data comes from.

The choice is not between "CQRS" and "no CQRS" as slogans. It is narrower: does a read use a
**second port with its own model**, or the **same repository outport** the writes use?

Two things constrain the answer and are easy to miss.

`ClassRoleRulesTest.repositoryOutportsExposeNoPersistenceType` already forbids a port from
returning a `Page`, a Spring Data type or a `*JpaEntity`. So "just return what the ORM gives
you" is not available in either design; both must express the result in the core's own
vocabulary.

And `architecture.definition.md` § 4.6 already reserves a home for the other answer —
`outbound.persistence.read` — which means choosing the simple option now costs nothing
structurally later.

## Decision

**Reads go through `MasterRepository`, the same outport the writes use.** It returns the
`Master` aggregate. The driver maps the aggregate to the use case's `Result`, and the GraphQL
adapter maps that to its payload — the same two-step mapping a write already performs.

There is **no** query port, **no** read model, **no** projection and **no** separate read
schema.

`Query.apiVersion` is replaced by `Query.master` in the increment that implements UC02. The
infrastructure field existed to avoid an accidental read side; a deliberate one retires it.

**The condition for revisiting is written here rather than left to judgement.** Write the
projection ADR when any one of these is first true:

1. A read needs data that spans more than one aggregate.
2. A read needs a shape the aggregate cannot produce without loading substantially more than
   the caller asked for.
3. A read's latency or query pattern is a stated requirement in its own right, rather than a
   consequence of the write model.

None of the three is true of "fetch a master and its contracts".

## Rationale

**At this size the read *is* the aggregate.** UC02 returns a master and the contracts inside
it — which is, exactly, the aggregate the repository already loads. A second port would
return the same fields through a second mapping, and the only guarantee it would add is that
the two mappings can disagree.

**Two adapters over identical data is a synchronisation problem invented for free.** The
argument for a read side is that reads and writes have genuinely different shapes and
different pressures. Where they do not, CQRS buys a consistency question — which adapter is
authoritative when they differ — in exchange for nothing.

**The aggregate boundary is what makes this safe.** `adr/0024` puts `Contract` inside the
`Master` aggregate, so loading a master already loads its contracts; there is no N+1 to
design around, because there is no second root to fetch. Had `Contract` been its own root,
this decision would be much weaker — which is worth noticing, because it means promoting
`Contract` later is also a reason to revisit this.

**Naming the revisit condition is the point of writing this down.** A decision to defer
becomes a decision to never do it when nobody records what "later" means. The three
conditions above are checkable by whoever writes the next read spec, and are meant to be
checked rather than admired.

## Consequences

- `MasterRepository` carries both write and read methods, and remains free of persistence
  types — the existing gate enforces that and needs no change.
- The GraphQL `Query` root gains its first domain field. Schema changes are breaking changes
  for the Backoffice UI (`adr/0020`), and `Query.apiVersion` is removed rather than kept
  alongside.
- `project.definition.md`'s Non-Goal recording the absence of a read side is replaced by a
  pointer to this ADR. An absence that has been decided is no longer an absence.
- `architecture.definition.md` § 4.6's `outbound.persistence.read` package stays empty and
  stays documented. It is the landing site for condition 1, 2 or 3 when one of them arrives.
- A read that grows past the three conditions and is added *without* the projection ADR is
  drift, and `ddd-hex-reviewer` should report it as such.
