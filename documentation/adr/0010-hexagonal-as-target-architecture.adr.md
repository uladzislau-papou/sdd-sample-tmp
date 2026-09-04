# ADR 0010 – Hexagonal Architecture as a Target, Not an Enforced Rule

## Status

Accepted

Unlike ADRs 0001–0009 and 0011, this one is **not** retroactive. It is a decision taken
when the SDD documentation set was adapted to this service, to resolve a contradiction
that was live in the repository.

## Context

`README.md` § Project Structure states:

> We aim to strictly adhere to the **Hexagonal (Ports and Adapters)** architecture,
> ensuring a clean separation between the **API**, **Application**, **Domain**, and
> **Infrastructure** layers.

The code does not do this and never has:

- there are no inbound or outbound ports — no `inport`, no `outport`, no interface between
  a service and the outside world that the service owns;
- services depend on Spring Data repository interfaces directly;
- the domain layer is Hibernate-annotated throughout (ADR 0003);
- outbound provider clients live under `api/integration`, which is not an
  infrastructure-side package by any reading of the name.

This contradiction is not harmless. An architecture document that describes something the
code is not produces two concrete failures:

1. **The reviewer is unusable.** An automated architecture review enforcing hexagonal
   rules against this codebase returns drift on essentially every file. A review that
   always fails is a review nobody reads.
2. **Partial migration is the worst outcome, and it is the likely one.** A well-meaning
   increment introduces `inport`/`outport` into one module because the README said to.
   The codebase then has two ontologies, and a reader must know which module they are in
   before they know where anything lives.

A decision was needed: enforce the aim, abandon it, or separate the two.

## Decision

**The layered module architecture of ADR 0002 is the enforced rule. Hexagonal is a
recorded target with no enforcement and no timeline.**

Concretely:

1. `architecture.definition.md` § 2.1 describes the layered structure and is what
   `rms-architecture-reviewer` enforces.
2. `architecture.definition.md` § 2.2 records the gap and explicitly overrides the
   README's claim for governance purposes.
3. **Existing JPA-annotated domain classes, direct repository dependencies and the
   `api/integration` location are not drift.** They are the documented model.
4. **Introducing `inport` / `outport` packages into any module is an ADR trigger**
   (`sdd.playbook.md` § 6 item 1). Not forbidden — gated.
5. A migration, if it ever happens, is **module-at-a-time with its own ADR**, never a side
   effect of feature work.

### What would make a migration worth doing

Stated now, while nobody is under pressure, so the question can be answered on evidence
rather than on taste:

- **Testability pain.** Service tests become expensive or unreliable because collaborators
  cannot be substituted cleanly. Currently they are cheap — mockito-kotlin over
  constructor-injected repositories works fine.
- **A second persistence technology.** If a module needs something other than JPA, an
  outbound port is the natural seam. This would be trigger 4 anyway.
- **Genuine extraction.** If a module is to become a separate service, ports are how the
  boundary is drawn first. Note that `onb → kyc` (117 imports) must be severed before this
  is even discussable.
- **Domain complexity outgrowing services.** If "which service enforces this rule?" stops
  having a quick answer, the model may need to carry more, and a persistence-ignorant
  model is how it would.

None of these hold today. That is why the target has no timeline.

### What is not a reason

- "The README says so." The README is being corrected by this ADR, not obeyed.
- "It is the better architecture." Possibly, in the abstract. This service needs a reason
  specific to itself.
- "We are already touching this module." Migration is never a side effect.

## Consequences

**Positive**

- The architecture document is true, so the reviewer can return a meaningful `PASS`.
- The aspiration is recorded rather than deleted — nobody has to re-derive it, and the
  next person who reads the README finds an answer instead of a contradiction.
- The trigger prevents accidental half-migration.

**Negative / accepted**

- **A documented aspiration with no timeline tends to stay documented.** This ADR may
  describe a state that never changes. That is preferable to a rule nobody follows.
- **The README remains inconsistent with the code** until someone edits it. This ADR
  overrides it for governance, but a new reader still meets the wrong claim first.
  Correcting the README is a small, worthwhile follow-up in the service repository — it is
  not done here because this repository does not own that file.
- Anyone joining with strong hexagonal expectations will find the codebase disappointing,
  and will find this ADR rather than an explanation-shaped silence. That is the intended
  outcome.

## Alternatives considered

- **Enforce hexagonal now.** Rejected: the reviewer would report the entire codebase as
  drift, every increment would be blocked, and the rule would be suspended within a week —
  leaving the project worse off than having no rule.
- **Delete the aspiration entirely.** Rejected: it is a legitimate direction, the
  conditions that would justify it are real, and deleting it means the next person
  re-derives the same debate from scratch.
- **Migrate one module as a pilot.** Rejected as a decision to take now, with no
  triggering problem. It remains available under trigger 1 when one of the four conditions
  above holds.
