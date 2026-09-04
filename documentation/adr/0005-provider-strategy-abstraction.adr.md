# ADR 0005 – Provider-Agnostic API with Per-Provider Strategies

## Status

Accepted (retroactive)

## Context

Identity verification and qualified electronic signature are supplied by three providers —
IDnow, PostIdent and Signius — with incompatible contracts:

- different session-creation payloads and identifiers,
- different status vocabularies and terminal states,
- different callback shapes and delivery guarantees,
- different document-retrieval mechanisms.

Product backends must not learn three vocabularies. Provider choice must be changeable —
by configuration, or per request — without a consumer-visible change. Adding a fourth
provider must not require touching every consumer.

## Decision

**Consumers see one provider-agnostic contract. Provider variation is absorbed behind
per-provider strategies.**

Structure in `qes`:

```
service/identification/strategy/{idnow,postident,signius}/    ← per-provider behaviour
service/signature/{idnow,postident,signius}/                  ← per-provider behaviour
api/integration/{idnow,postident,signius}/                    ← HTTP clients + provider models
mapper/{idnow,postident,signius}/                             ← provider vocabulary ↔ ours
domain/enums/{idnow,signius}/                                 ← provider enums, quarantined
api/controller/{idnow,postident,signius}/                     ← provider webhook endpoints
```

Rules:

1. **Provider models never leave `api/integration`.** A `…integration.idnow.model.*` type
   in a service signature, an entity, a DTO or a GraphQL schema is drift
   (`architecture.definition.md` § 4.6).
2. **Translation is total.** Every provider status maps to one of ours or fails loudly.
   `test.definition.md` § 2.2 requires the mapping test to cover every source value, and
   `coding-style.definition.md` § 2.3 forbids an `else` branch over a provider enum.
3. **Provider selection is configuration or a request parameter**, resolved once and
   dispatched to a strategy. Consumers do not branch on provider.
4. Provider-specific passthrough is permitted where a product needs it, but it is
   explicit in the contract, not an escape hatch.
5. Provider failures surface as typed exceptions from `domain/exception`
   (`SigniusIntegrationException`, `PostIdentIntegrationException`, …), never as raw
   `RestClientException`.

## Consequences

**Positive**

- A product backend integrates once. Switching provider is a configuration change.
- A provider's contract change is contained to its strategy, client and mapper.
- Adding a provider is an additive change: one strategy, one client, one mapper package,
  one webhook controller.
- The exhaustive-mapping rule means a provider adding a status **breaks the build** rather
  than silently stalling a session.

**Negative / accepted**

- **The normalized vocabulary is a lowest-common-denominator.** A capability only one
  provider offers is either absent from the common contract or exposed as documented
  passthrough. Both are worse than a native integration for the consumer who wants it.
- **Threefold surface area.** Three strategies, three clients, three mapper packages,
  three webhook endpoints, each with its own tests. The mapping tests in particular are
  the largest single block of test code in `qes`, and that is the intended trade.
- **Provider enums live in `domain/enums/{idnow,signius}/`**, not in `api/integration`.
  That is a partial leak of provider vocabulary into the domain package, accepted because
  the enums are the translation table's source column and are never persisted as our
  status.
- **Provider-specific behaviour can hide in a strategy.** A business rule that belongs in
  the shared path but is implemented in one strategy diverges silently. Review must check
  that a rule appearing in one strategy either appears in all or genuinely is
  provider-specific.

## Constraints

- **Onboarding a new provider, or changing which provider is the default for an operation,
  is an ADR trigger** (`sdd.playbook.md` § 6 item 14). A provider is a contract, an SLA and
  a failure mode, not a config value.
- A new provider requires an integration spec
  (`documentation/integrations/*.outbound.spec.md`) with a complete § 3 translation table
  before implementation.

## Alternatives considered

- **One provider only.** Rejected — provider availability and commercial terms are not
  under our control, and the platform already uses more than one.
- **Expose provider differences to consumers.** Rejected; that is the problem RMS exists
  to solve.
- **A generic passthrough proxy with no normalization.** Rejected — it moves the
  translation burden to every consumer and makes provider switching a consumer-visible
  breaking change.
