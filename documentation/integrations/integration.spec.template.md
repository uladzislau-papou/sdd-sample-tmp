# Integration Specification – <Name>

<!--
File name:
  <kebab-name>.inbound.spec.md   — a surface the outside world calls
  <kebab-name>.outbound.spec.md  — something RMS calls or delivers to
(`file-naming.definition.md`).

RMS has no ports (`architecture.definition.md` § 2.2). "Inbound" and "outbound"
name what the code actually is: controllers and filters on one side, clients and
delivery services on the other.

Delete the direction block that does not apply.
-->

## Status
SPECIFIED | IMPLEMENTED | SUPERSEDED

## Direction
INBOUND | OUTBOUND

## Module
`<qes | kyc | onb | boni | common>`

## Implemented by

| Role | Class |
|------|-------|
| | `com.jobradleasing.riskmanagementservice.…` |


## 1. Responsibility

One paragraph. What this integration is for, and what it must never do.


## 2. Contract

### If INBOUND

| Operation | Surface | Scope | Use case |
|-----------|---------|-------|----------|
| | GraphQL mutation / `POST /…` / webhook | `api.<module>.write` | `uc<nn>` |

Authentication: `<static JWT scope | provider signature | HMAC | open, and why>`

Schema / wire contract:

```
<graphql SDL fragment or JSON body>
```

### If OUTBOUND

| Operation | Method + path | Timeout | Retry | Idempotency key |
|-----------|---------------|---------|-------|-----------------|
| | | | | |

Base URL and credentials come from `<module>.config.property.<Name>Properties`, backed by
`<ENV_VAR>` in `.env.example`.


## 3. Vocabulary Translation

**The table that matters most.** Every value of the external vocabulary maps to exactly
one of ours, or fails loudly (`test.definition.md` § 2.2).

| External value | Our value | Notes |
|----------------|-----------|-------|
| | | |
| *(unknown)* | — | throws `<Exception>`; never defaults |

Translating class: `<module>.api.mapper.<Name>Mapper`

Model classes that MUST NOT escape `api/integration`
(`architecture.definition.md` § 4.6):

- `…integration.<provider>.model.*`


## 4. Error Mapping

| External failure | Our exception | Surfaced as |
|------------------|---------------|-------------|
| 4xx from provider | | |
| 5xx from provider | `<Provider>IntegrationException` | 502 |
| timeout | | |
| malformed payload | | |

No raw `RestClientException` reaches a service. No provider payload reaches a log line or
an error message.


## 5. Idempotency and Delivery

- Is a repeat safe? What makes it safe — a natural key, a dedup table, a status guard?
- For an outbox-backed delivery: which table, which delivery service, which scheduler,
  what the retry policy is, and what happens after the final attempt.
- For an inbound webhook: how duplicate provider callbacks are detected and discarded.


## 6. Transactional Expectations

- Does the caller's transaction span this call? (It should not span a network call —
  state why if it does.)
- Where the outbox row is written relative to the business transaction.
- What is rolled back and what is not, on failure.


## 7. Configuration

| Key | Env var | Default | Required in |
|-----|---------|---------|-------------|
| | | | local / dev / prod |

Every variable listed here MUST be present in `.env.example`
(`test.definition.md` § 7 gate 13).


## 8. Observability

- Which metrics are emitted (`qes/metrics/` pattern, if applicable)
- What is logged at INFO / WARN / ERROR, and what must never be logged
- Which audit events, if any, this integration produces


## 9. Failure Scenarios

- Provider unavailable
- Provider returns an unmapped value
- Credentials invalid or expired
- Payload too large / malformed
- Duplicate delivery
- Partial success (some rows delivered, some not)


## 10. Test Requirements

Expressed as DoD items in the use case specs that touch this integration. Those items
must cover:

- Every row of the § 3 translation table
- The unknown-value failure path
- Every row of the § 4 error mapping
- Idempotency: the same input twice produces one effect
- For inbound: 401, 403, and pass-through (`test.definition.md` § 2.4)
- For outbound: client behaviour with a stubbed HTTP layer, including timeout and retry
