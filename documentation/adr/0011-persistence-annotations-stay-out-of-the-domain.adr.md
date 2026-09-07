# ADR 0011 – Persistence Annotations Stay Out of the Domain

## Status
Accepted (inherited from template)

## Context

ADR-0001 kept the domain clean by **banning JPA outright**: "We explicitly do NOT use JPA,
Hibernate, Spring Data repositories."

The donor service this template takes its stack from does the opposite — Spring Data JPA,
with its `@Entity` classes sitting in `domain/model` packages. That is precisely the leak
the ban was meant to prevent, and it happened in a codebase where JPA was permitted and no
rule checked where its annotations lived.

Those two facts point at the same conclusion from opposite directions: the ban was solving
the wrong problem. A banned technology is one source of the leak; the leak is the problem.

## Decision

The template fixes **one** persistence rule:

> **No type in `core` or in `shared.domain` / `shared.outport` may depend on a persistence
> API.**

Enforced by `DependencyRulesTest.rule6_noPersistenceTypeInTheCore`, which forbids
`jakarta.persistence`, `org.hibernate` and `org.springframework.data` from those packages,
and by `ClassRoleRulesTest.repositoryOutportsExposeNoPersistenceType`, which stops an
entity or a `Page` appearing in an outbound port's signature.

The **ORM is not fixed**. `technical.spec.md` is a project profile (`CLAUDE.md`), so each
service chooses. The example uses Spring Data JPA with `*JpaEntity` types in
`outbound.persistence` and a mapper to the aggregate.

## Rationale

**Why name the guarantee instead of the technology.** A rule about the leak catches the
next ORM, an in-house JDBC mapper and a serialization framework. A ban on JPA catches JPA.
The donor service is the evidence: JPA was allowed, no rule existed, and the annotations
walked into the domain. Had the rule been "no persistence annotations in the domain", the
first `@Entity` in `domain/model` would have failed the build.

**Why fixing the ORM would have contradicted an earlier decision.** Making jOOQ mandatory
in the template's core would put a technology choice in a document
(`technical.spec.md`) that was deliberately made a per-project profile. One of the two
decisions had to give, and the one worth keeping is the profile.

**Why JPA for the example specifically.** It is the donor's stack, Kotlin/Spring teams know
it, and jOOQ's code generation ties the build to a live database schema — a cost every new
service pays on day one and the template's author pays forever in support.

**A property of the choice, not of the rule.** JPA's merge writes the whole entity, so the
"partial update" defect recorded in `ports/tour-booking-repository.outport.spec.md` § 2.3 —
where hand-written SQL updated only the columns its author remembered and silently
discarded a use case's effect — cannot happen through this adapter. That is a bonus, not
the reason. The obligation stays written in the port spec, because the next adapter may be
hand-written SQL again.

## Consequences

- Two representations of the same data, and a mapper between them. That duplication is the
  price and also the benefit: a column can be renamed without touching an invariant, and an
  invariant can change without a migration.
- The domain cannot be persisted "for free". Nobody may annotate an aggregate to save a
  mapper — the build fails.
- A service switching ORM rewrites `outbound.persistence` and nothing in `core`. That claim
  is what the rule exists to keep true.
