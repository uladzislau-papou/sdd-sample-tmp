# ADR 0020 – GraphQL Is the Only Transport in the MVP

## Status
Accepted

## Context

`adr/0012-dual-delivery-transports.adr.md` establishes that this codebase supports REST and
GraphQL as parallel transports over one core, and that the `api/` folder holds one
executable request file per transport a use case actually uses. It does not require a use
case to use both.

The consumer is known. The platform's frontend monorepo — the shell and every backoffice
application in it, including the one for the donor service — depends on `@apollo/client` and
`graphql`, and on no HTTP client or OpenAPI code generator. The Backoffice UI for contract
management will speak GraphQL.

One machine consumer is anticipated but not connected: the refinancing domain. The risk
register asks when an individual lease becomes visible to it, and calls for the API contract
to be **defined** in the MVP even though refinancing is not yet integrated.

## Decision

GraphQL is the only transport in the MVP. No REST controllers are written, and the
`springdoc-openapi` dependency is not added — it describes REST, and there is no REST to
describe.

REST arrives with the first machine consumer that needs it. At that point springdoc is added
in the same increment, and `adr/0012-dual-delivery-transports.adr.md` needs no revision,
because it always permitted this.

The refinancing contract is answered where the question was asked — as an **outbound port
specification** under `documentation/ports/` stating what refinancing must learn and at
which point in a lease's lifecycle it becomes visible. Not as an unimplemented endpoint.

## Rationale

**A transport with no consumer cannot be verified.** A REST controller nobody calls is
exercised only by the test written next to it, and it drifts from the domain silently until
a consumer arrives and finds the shape wrong. The `api/` files exist precisely because a
contract needs to be executable against something real.

**"Define the API contract" is a specification request, not an endpoint request.** What
refinancing needs to know, and when, is a domain question — the answer is a document that
outlives whichever transport eventually carries it. Writing an empty endpoint would answer
the transport question, which nobody asked, and leave the timing question open.

**Deferring springdoc keeps a dependency honest.** It was originally justified by "the
frontend needs a machine-readable schema". The frontend does, and the GraphQL schema in
`resources/graphql` already is one, by construction.

## Consequences

- `api/` holds only `.graphql` files until REST arrives.
- The GraphQL schema is the API contract for the Backoffice UI, and schema changes are
  breaking changes for it.
- The GraphQL `Query` root must exist even before this service has a read side. What
  currently sits under it is one infrastructure field, and turning it into a domain read
  requires the read-side decision recorded as a Non-Goal in `project.definition.md` — not a
  quiet addition to the schema.
