# Project Vision: Risk Management Service

## Purpose

Risk Management Service (RMS) is the **unified identity, signature and risk backend**
for the JobRad Leasing platform. Product backends integrate once; RMS absorbs the
variation behind that single contract.

It answers three questions on behalf of every consuming product:

1. **Who is this person?** — identity verification via eID, VideoIdent and PostIdent.
2. **Did they sign it?** — Qualified Electronic Signatures under eIDAS.
3. **May we do business with this company?** — KYC: company core data, functionaries,
   UBOs, sanctions/PEP screening, transparency-register reconciliation, and a
   case decision.

Plus the credit-decision intake (`boni`) and the onboarding integration (`onb`) that
delivers KYC progress and decisions back to RADar.

## Problem

Each of these domains is supplied by **external providers with incompatible contracts**,
different lifecycles, and different failure modes:

| Concern | Providers |
|---------|-----------|
| Identification | IDnow, PostIdent, Signius |
| Signature | IDnow eSign, PostIdent, Signius |
| KYC data & screening | KYCnow |
| Onboarding / credit | RADar |

Without a unifying service, every product backend integrates with every provider,
learns every provider's vocabulary, and re-implements session lifecycle, webhook
handling, retry and audit for each one.

The recurring failure modes RMS exists to prevent:

- Provider vocabulary leaking into product backends
- Each product inventing its own webhook idempotency and retry story
- Regulated decisions (KYC accept/decline, identification outcome) with no audit trail
- Silent divergence between the state a provider believes and the state we recorded

## Core Focus

RMS models:

- **Session lifecycle** — identification and signature sessions from creation through
  provider callback to a terminal, normalized status
- **Case lifecycle** — the KYC case state machine, from intake through screening to an
  operator's terminal decision
- **Provider abstraction** — one strategy per provider behind a common interface;
  provider selection by configuration or request parameter
- **Normalized webhooks** — provider callbacks translated into our vocabulary, exactly once
- **Outbound delivery with retry** — client callbacks, RADar progress and decision
  delivery, audit emission, all via durable outbox tables and schedulers
- **Auditability** — regulated actions produce CloudEvents attributed to a named operator

Correctness and traceability over convenience.

## Architectural Stance

RMS is a **modular monolith with a layered internal structure**. This is a statement of
what it is, not an aspiration:

- One deployable Spring Boot application, one PostgreSQL database
- Four feature modules (`qes`, `kyc`, `onb`, `boni`) plus a shared `common` module
- Each module layered `api` → `service` → `repository` → `domain`
- Business logic lives in `@Service` classes; `@Entity` classes carry state and
  persistence mapping
- Transactions are owned by services, one per inbound operation
- Provider integrations sit behind per-provider strategies

`architecture.definition.md` § 2 records the gap between this and the
Ports & Adapters structure the repository README aspires to, and
`adr/0002-layered-module-architecture.adr.md` records why the layered structure is
the enforced rule today.

## Development Doctrine

- Spec before implementation
- ADR before architectural change
- Small, verifiable increments
- Test-first for new behaviour, with the RED failure quoted as evidence
- Every regulated action emits an audit event
- Every provider contract change is a spec change first

## Non-Goals

- **Not a microservice split.** One deployable, one database. Module boundaries are
  compile-time conventions, not network boundaries.
- **Not a rules engine.** Risk policy lives with the products that own it; RMS executes
  and records decisions, it does not author policy.
- **Not a provider-feature showcase.** Provider-specific capability is exposed only where
  a product needs it; passthrough is bounded and documented.
- **Not a UI.** The backoffice consumes the GraphQL API; RMS ships no frontend.
- **Not eventually consistent by default.** Cross-module coordination is synchronous
  unless a spec says otherwise; the outbox tables exist for outbound delivery to
  *external* systems, not for internal decoupling.
