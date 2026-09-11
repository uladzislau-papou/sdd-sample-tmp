# ADR 0008 – GraphQL as the Only Inbound Adapter

## Status
Accepted

## Context

The system needs an inbound transport. The surrounding service landscape uses both:
`risk-management-service` exposes its KYC and identification surfaces over GraphQL
and its BONI surface over REST, which is evidence that both are acceptable here and
no evidence about which to choose.

The SDD framework this project inherited was built around REST, and its
assumptions are visible throughout: a `*RestAPI` interface holding the HTTP
annotations, a `rest/*.http` file per use case, use-case specs with an HTTP status
mapping table, and quality gate 13 requiring those files to reflect the code.
Adopting GraphQL means rewriting that machinery, so the choice is not free.

Three options:

1. REST only — keep the inherited framework unchanged.
2. GraphQL only.
3. Both, as `risk-management-service` does.

## Decision

**GraphQL only.** There is no REST controller, no `rest/` folder and no springdoc
in this application.

Consequences for the framework, all of which are made rather than deferred:

- `architecture.definition.md` § 4.5 describes an `inbound.graphql` package with a
  different shape from the REST one: **no `*GraphQLAPI` interface**, because the
  schema is the contract.
- Use-case specs replace the HTTP status table with an **error classification**
  table, because every GraphQL response is `200 OK`.
- `rest/uc<nn>-*.http` becomes `graphql/uc<nn>-*.graphql`.
- `file-naming.definition.md` distinguishes schema files (`.graphqls`, in
  `src/main/resources/graphql/`) from request files (`.graphql`, in `graphql/`).

## Rationale

### Why GraphQL fits this domain

The read shape is genuinely graph-like and genuinely varied. A master leasing
contract has a current configuration, a version history, a parent contract, a
service agreement, and N individual contracts each with their own configuration.
Different consumers need different slices: a manager portal wants the contract with
its eligible-employee count; an operations view wants a contract with every live
lease; an audit view wants configuration versions and nothing else.

Over REST that is either several endpoints that overlap, one endpoint with an
`?expand=` parameter, or an endpoint per screen. All three are shapes that decay.
A schema lets the caller state which slice it wants, once.

### Why the schema being the contract is worth more than it sounds

This is the decisive argument for a **spec-driven** project specifically.

The inherited framework put the HTTP contract on a `*RestAPI` interface because
otherwise it existed only as annotations scattered across method signatures, and a
spec claiming "returns 409 on an invalid transition" could disagree with the code
indefinitely without anything noticing.

A GraphQL schema is a checked artefact. The server validates resolvers against it at
startup: a field with no resolver fails to boot, and a resolver with no field is dead
code a test catches. `@GraphQlTest` loads the schema, so the schema is under test
without anyone writing a test for it. That converts a class of spec/code drift —
"the documented operation does not exist" — from a review concern into a build
failure, which is exactly the trade this project exists to make.

### Why not both

Because the cost is not the second adapter, it is the second *doctrine*. Two
transports mean two class-role conventions, two error-mapping mechanisms, two
request-file conventions, two sets of quality-gate wording, and a per-use-case
decision about which surface it belongs on. A reference implementation demonstrating
a clean hexagon should demonstrate one inbound adapter done properly, and the
hexagon's actual claim — that the core does not care about the transport — is
demonstrated by the core not mentioning GraphQL anywhere, not by wiring two
adapters to prove it.

If a REST surface is ever genuinely needed for an integration that cannot speak
GraphQL, adding it is a new inbound adapter over the same inports, which is the
architecture working. It is also an ADR.

### What is given up

Stated plainly, because these are real:

- **HTTP semantics.** No status codes, no `Cache-Control`, no conditional requests,
  no CDN caching of a GET. Everything is a `POST` returning `200`. For an internal
  contract-administration API this costs little; for a public read-heavy API it
  would cost a lot.
- **Error handling is now a convention rather than a standard.** A 404 is
  universally understood; `extensions.classification: NOT_FOUND` is understood by
  whoever reads our schema. This is why `architecture.definition.md` § 4.5 fixes the
  mapping table rather than leaving each resolver to invent one.
- **`curl` ergonomics.** A GraphQL request is a JSON body containing a query string.
  The `graphql/*.graphql` files exist partly to keep the API explorable by hand.
- **Query cost is unbounded by default.** A sufficiently nested query can be
  expensive, and nothing here limits depth or complexity yet. See *Future
  Considerations*.

### Why no springdoc / OpenAPI

It documents REST. With no REST surface there is nothing for it to document, and the
schema plus GraphiQL cover the same need natively.

## Consequences

- The `inbound.graphql` package has three roles (`*GraphQLController`, `*Input`,
  `*Payload`) plus `*ExceptionResolver`, rather than the five a REST adapter needs.
- Every use case spec § 9 documents a GraphQL operation and an error classification
  per failure, not a route and a status.
- `spec-documenter` reconciles `graphql/*.graphql` instead of `rest/*.http`.
- ArchUnit gains a rule: no `graphql.*` or `org.springframework.graphql` import
  outside `inbound.graphql` (ADR 0007).
- The GraphQL schema is a published contract. A breaking change to it — removing a
  field or operation, renaming one, tightening nullability, removing an enum value —
  is an ADR trigger (`sdd.playbook.md` § 6 item 12). Adding an optional field is not.

## Future Considerations

- **Query complexity and depth limits.** Spring GraphQL supports both via
  `ConfigurableGraphQlSourceBuilder` instrumentation. Not configured, because the
  current schema has no recursive edge deep enough to abuse. It becomes necessary the
  moment a contract exposes its leases and a lease exposes its contract.
- **DataLoader.** Not needed while no resolver traverses — § 4.5 forbids a
  `@SchemaMapping` that loads. It becomes necessary the first time that rule is
  legitimately relaxed for a read-side projection, and that relaxation is itself worth
  an ADR.
- **Subscriptions** are unused and out of scope; adding them is event-driven
  processing (`sdd.playbook.md` § 6 item 6).
