---
name: banking-stack-testing
description: Run and verify the complete Docker Compose banking stack through its gateway and service dashboards, without assuming stale Postman settings match runtime configuration.
---

# Full-stack banking runtime testing

This complements the core-module development skill with cross-service runtime mechanics.

## Start and identify the runtime

From the repository's `docker-compose` directory run `docker compose up -d --build`.
Allow a few minutes for startup and discovery. Expect 11 running containers and
five Eureka client applications (core, gateway, users, transfers, utility payments).
Registry and config-server do not appear as client registrations.

Compose uses `image:` rather than `build:` for Java services; `--build` does not
rebuild them from the checkout. Explicitly identify whether testing published
images or locally built source is intended. Do not reset persistent volumes merely
to recover seed balances; record the current baseline instead.

## Credentials and token handling

### Devin Secrets Needed

No additional Devin-managed secrets are required when the task supplies the seeded
local admin password. For other environments, obtain an authorized test username,
password, and client configuration; do not assume local seed credentials apply.

Use realm `javatodev-internet-banking` and client
`javatodev-internet-banking-api-client`. Read the matching client secret from
`docker-compose/keycloak/realm-export.json` at runtime, not the old Postman env.
Use a password grant at
`http://localhost:8080/realms/javatodev-internet-banking/protocol/openid-connect/token`
(modern Keycloak has no `/auth` prefix). Keep passwords and JWTs out of saved
evidence; tokens may expire after 300 seconds.

## Requests and assertions

Inspect live gateway route logs/configuration. Published configuration may use
`/banking-core/**` and `/utility-payment/**` rather than the older collection's
`/core/**` and `/payment/**`. `/fund-transfer/**` is the transfer-service prefix.

- GET `/banking-core/api/v1/user`.
- GET `/banking-core/api/v1/account/bank-account/{number}`.
- POST `/fund-transfer/api/v1/transfer` with fromAccount, toAccount, amount.
- POST `/utility-payment/api/v1/utility-payment` with providerId, amount,
  referenceNumber, account.
- GET the same service POST paths to read transaction records.
- Repeat a protected users GET without Authorization; require HTTP 401.

Seed accounts 100015003000 and 100015003001 are useful, but read balances before
mutation. Independently verify actual and available balances after each operation:
HTTP 200 and success messages alone do not establish accounting correctness.
Check transfer records by transactionReference; payment-list DTOs may omit
transactionId, so correlate account/provider/reference/amount and SUCCESS.
Utility payments here exercise internal processing, not external-provider settlement.

## Browser evidence

Record Eureka `http://localhost:8081/`, the Keycloak realm account console
`http://localhost:8080/realms/javatodev-internet-banking/account/`, and Zipkin
`http://localhost:9411/zipkin/`. On Zipkin click Run Query, then Show on a POST
trace to visualize gateway → service → core. Account-console availability is
not evidence of browser login; API password-grant evidence is separate.

Keep API responses and final Compose state as redacted JSON/text evidence.
Do not record an idle browser during shell-only API requests. Leave the stack
running when requested.
