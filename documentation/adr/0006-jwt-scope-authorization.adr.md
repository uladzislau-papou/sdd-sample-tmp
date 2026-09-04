# ADR 0006 – Static JWT Scope Authorization, Unverified Operator Identity

## Status

Accepted (retroactive)

## Context

RMS has two distinct identity questions, and conflating them was the trap this decision
had to avoid:

1. **Which system is calling, and may it perform this operation?** Callers are internal
   product backends and the backoffice. There is no end-user login against RMS.
2. **Which human is behind this change?** KYC decisions are regulated; an audit event that
   says "the system accepted this case" is worthless.

Additional constraint: the service sits behind an AWS-internal auth gateway that has
already validated the platform session token before forwarding a request.

## Decision

### Service authorization — static JWT with scopes

- HS256 bearer token on every GraphQL request and every ONB REST request.
- The HMAC key is **SHA-256 of `AUTH_JWT_SECRET`**, so a secret of any length works.
- `sub` must equal `auth.jwt.expected-subject` (default `Riskmanagement service`).
- `scope` is OAuth2-style: a space-delimited string or a JSON array.
- **No `exp` / `nbf` / `iat` validation.** Tokens are static and provisioned.
- `HS256` is enforced explicitly; `HS384`, `HS512` and `none` are rejected even with the
  same secret.

Scope taxonomy:

| Surface | Read | Write |
|---------|------|-------|
| QES GraphQL | `api.qes.read` | `api.qes.write` |
| KYC GraphQL | `api.kyc.read` | `api.kyc.write` |
| ONB REST | `api.onb.read` (GET/HEAD) | `api.onb.write` (POST/PUT/PATCH/DELETE) |
| RADar webhook | — | `credit.radar.webhook` |

Enforcement points: GraphQL interceptors in root `web/auth/`; servlet filters in
`<module>/web/auth/`.

Deliberately open: `/actuator/**`, and the provider webhooks
`/webhooks/{idnow,postident,signius}`, which authenticate by provider-specific means.

Failure: `401` for missing/invalid token, `403` for valid token with insufficient scope.

### Operator identity — decoded, not verified

The acting human comes from the platform session token in the `access_token` cookie,
which RMS **decodes without verifying the signature**.

Rationale: the AWS-internal auth gateway already validated that exact token — signature,
expiry and session — before forwarding. Re-checking here would require RMS to hold the
platform's signing key, which is a larger secret-management liability than the check is
worth.

Absent the cookie — schedulers, webhooks, ONB, local calls — actions are attributed to
`svc:risk-management-service` (`common.audit.AuditActors`).

**The acting operator is never taken from request input.** An `input.actor` field is not
the operator; `OperatorContext` is (`architecture.definition.md` § 8.2).

## Consequences

**Positive**

- No token lifecycle to operate: no refresh, no rotation coordination with consumers, no
  clock-skew failures.
- Scope granularity matches the real boundary — per module, per read/write.
- Audit attribution is trustworthy without RMS holding platform signing keys.
- Explicit algorithm pinning closes the classic `alg: none` and algorithm-confusion
  attacks.

**Negative / accepted**

- **A leaked `AUTH_JWT_SECRET` is unbounded and permanent.** With no `exp`, a forged token
  never expires; the only remedy is rotating the secret and re-provisioning every consumer.
  The README says to treat it like a password, and that is the whole mitigation.
- **No revocation.** Withdrawing a consumer's access means rotating the shared secret,
  which affects all consumers.
- **Operator identity is only as trustworthy as the gateway.** If a request ever reaches
  RMS without traversing the gateway, the `access_token` cookie is attacker-controlled and
  audit attribution is forgeable. This is a **deployment invariant, not a code
  invariant** — nothing in RMS enforces it. It must hold in every environment where audit
  events are treated as evidence.
- **The dev-mode random secret** logged at WARN on startup is a convenience that must
  never be relied on in a deployed environment.
- Scope is coarse: `api.kyc.write` authorizes every KYC mutation, including terminal
  decisions. Finer authorization would need a different model.

## Constraints

- **Changing the authentication or authorization model is an ADR trigger**
  (`sdd.playbook.md` § 6 item 15).
- Every new externally reachable endpoint declares its scope and has authorization tests
  for 401, 403 and pass-through (`test.definition.md` § 2.4). An endpoint shipped without
  them is a security regression, not a coverage gap.

## Alternatives considered

- **Full OAuth2 / OIDC with a real authorization server.** Rejected for now: no end-user
  login against RMS, and the operator identity problem is already solved by the gateway.
  This is the natural successor if RMS ever serves an untrusted caller.
- **mTLS between services.** Rejected — heavier to operate on the current substrate, and
  it answers only question 1.
- **Verifying the platform session token in RMS.** Rejected — requires holding the
  platform's signing key for a check the gateway has already performed.
