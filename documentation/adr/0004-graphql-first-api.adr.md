# ADR 0004 – GraphQL as the Primary API, REST for Integrations

## Status

Accepted (retroactive)

## Context

RMS serves two very different kinds of caller:

1. **Internal product backends and the backoffice UI** — they need to read composite
   views of a KYC case (company, functionaries, UBOs, screening matches, documents) and
   issue operator commands. Their needs vary per screen and change often.
2. **External systems** — provider webhooks (IDnow, PostIdent, Signius), the RADar
   decision webhook, and the ONB integration. Their contracts are fixed by the other side
   or by an integration agreement.

A single API style would serve one of these badly.

## Decision

**GraphQL is the primary API for internal consumers. REST is used for integrations.**

| Surface | Style | Endpoint |
|---------|-------|----------|
| QES (identification, signature) | GraphQL | `POST /riskmanagement/qes/v1/graphql` |
| KYC | GraphQL | `POST /riskmanagement/kyc/v1/graphql` |
| ONB integration | REST | `/riskmanagement/onb/kyc/v1/*` |
| Provider webhooks | REST | `/webhooks/{idnow,postident,signius}` |
| RADar decision webhook | REST | `/webhooks/radar/decisions` |

Schemas live in `src/main/resources/graphql/`, one directory per domain
(`identification/`, `signature/`, `kyc/`, `common/`), composed onto `root.graphqls`'s
`Query` and `Mutation` types via `extend type`.

Controllers are Spring GraphQL `@Controller` classes with `@QueryMapping`,
`@MutationMapping` and `@SchemaMapping`.

**The schema is part of the contract.** A controller method with no schema field, or a
schema field with no controller method, is a broken contract
(`architecture.definition.md` § 4.5).

## Consequences

**Positive**

- The backoffice fetches exactly the KYC case projection a screen needs, in one round
  trip, without RMS shipping a new endpoint per screen.
- Schema-first composition via `extend type` lets each domain own its slice without a
  central file that every change touches.
- `GraphQlTester` makes controller tests cheap and close to the wire.
- Integrations get a REST contract, which is what every provider and integration partner
  actually expects.

**Negative / accepted**

- **Two API styles to maintain**, with two error models. GraphQL returns HTTP 200 with an
  `errors` array and an `extensions.classification`; REST returns status codes. Error
  translation is therefore duplicated: `GraphQlControllerAdvice` and
  `KycGraphQlErrorInterceptor` on one side, `OnbRestExceptionHandler` on the other.
- **Authorization is style-specific.** GraphQL scope enforcement lives in interceptors
  (`web/auth/OperationScopeEnforcingGraphQlInterceptor`,
  `PathAwareScopeEnforcingGraphQlInterceptor`); REST scope enforcement lives in servlet
  filters. A new surface must use the right mechanism, and
  `test.definition.md` § 2.4 makes tests for it mandatory.
- **Query cost is caller-controlled.** Nothing in the current design bounds query depth or
  complexity. This is acceptable only because every caller is internal and authenticated
  with a static, provisioned token. It stops being acceptable the moment an untrusted
  caller is added — that would be trigger 15.
- **Schema drift is possible.** The schema and the controller are separate files that must
  agree. `test.definition.md` § 2.3 requires a controller test per operation, which is what
  catches it.
- Versioning is by path (`/v1/`) on both styles, which sits awkwardly with GraphQL's usual
  additive-evolution model. Changing that is trigger 12.

## Alternatives considered

- **REST everywhere.** Rejected for the KYC read surface: the composite case projection
  would need either many round trips or a proliferation of screen-specific endpoints.
- **GraphQL everywhere, including webhooks.** Rejected. Providers post what they post;
  we do not get to choose their contract.
- **gRPC for internal consumers.** Rejected. No existing platform tooling, and the
  backoffice is a browser client.
