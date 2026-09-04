# `rest/` — Executable API Request Documentation

One JetBrains HTTP Client file per use case:
`uc<nn>-<use-case-name>.http`, matching the spec filename stem
(`file-naming.definition.md`).

Each file covers the **happy path plus one request per documented outcome** in the use
case spec § 9 — every error classification, every status. A documented `403` with no `403`
request is an incomplete contract, and `spec-documenter` reports it.

These files complement the Playwright suite in `playwright/api/`. Playwright asserts;
these document and let a human reproduce a call by hand.

---

## Environment

Create `rest/http-client.env.json` locally — **it is git-ignored, never commit it**:

```json
{
  "local": {
    "host": "http://localhost:8080",
    "qesToken": "<jwt with api.qes.read api.qes.write>",
    "kycToken": "<jwt with api.kyc.read api.kyc.write>",
    "onbToken": "<jwt with api.onb.read api.onb.write>",
    "radarToken": "<jwt with credit.radar.webhook>"
  }
}
```

Generate tokens with `node scripts/generate-jwt.js {qes|kyc|onb|all}` in the service
repository (`README.md` § Authentication).

---

## GraphQL requests

RMS is GraphQL-first. A GraphQL operation is a `POST` with the query in the JSON body.

```http
### UC03 – decline a KYC case (happy path)
POST {{host}}/riskmanagement/kyc/v1/graphql
Content-Type: application/json
Authorization: Bearer {{kycToken}}

{
  "query": "mutation DeclineCase($input: DeclineCaseInput!) { declineCase(input: $input) { id status declineReason declineSource } }",
  "variables": {
    "input": {
      "caseId": "00000000-0000-0000-0000-000000000000",
      "reason": "Sanctions match confirmed"
    }
  }
}

> {%
    client.test("returns DECLINED", function() {
        client.assert(response.body.data.declineCase.status === "DECLINED");
    });
%}
```

Notes:

- Keep the operation name in the query — it shows up in logs and makes the request
  self-describing.
- GraphQL errors return HTTP 200 with an `errors` array. An error case is asserted on
  `errors[0].extensions.classification`, not on the status code:

```http
### UC03 – decline a case already in a terminal state (BAD_USER_INPUT)
POST {{host}}/riskmanagement/kyc/v1/graphql
Content-Type: application/json
Authorization: Bearer {{kycToken}}

{
  "query": "mutation { declineCase(input: { caseId: \"…\", reason: \"…\" }) { id } }"
}

> {%
    client.test("classified BAD_USER_INPUT", function() {
        client.assert(response.body.errors[0].extensions.classification === "BAD_USER_INPUT");
    });
%}
```

## REST requests

```http
### ONB – fetch parties for an intake (happy path)
GET {{host}}/riskmanagement/onb/kyc/v1/{{intakeId}}/parties
Authorization: Bearer {{onbToken}}
```

## Authorization cases

**Every file MUST include the 401 and 403 cases** for its surface — they are part of the
contract, and `test.definition.md` § 2.4 makes them mandatory in the test suite too.

```http
### UC03 – no token (401)
POST {{host}}/riskmanagement/kyc/v1/graphql
Content-Type: application/json

{ "query": "{ __typename }" }

### UC03 – wrong scope (403)
POST {{host}}/riskmanagement/kyc/v1/graphql
Content-Type: application/json
Authorization: Bearer {{qesToken}}

{ "query": "mutation { declineCase(input: { caseId: \"…\", reason: \"…\" }) { id } }"
}
```

## Provider webhooks

Webhooks authenticate by provider-specific means, not the platform JWT. Document a
representative payload and the duplicate-delivery case:

```http
### Signius – signature completed callback
POST {{host}}/webhooks/signius
Content-Type: application/json

{ }

### Signius – same event delivered twice (must be idempotent)
POST {{host}}/webhooks/signius
Content-Type: application/json

{ }
```

---

## Rules

- Never commit a real token, a real personal identifier, or a real provider payload
  containing personal data. Use obviously synthetic values.
- Update the file in the **Implement Phase**, not afterwards.
- One `###` block per outcome, titled with the use case number and what it exercises.
