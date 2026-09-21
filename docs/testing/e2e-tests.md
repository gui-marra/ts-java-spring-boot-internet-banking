# End-to-end tests

Part of the [testing strategy](../../TESTING.md).

**Definition:** black-box tests against the full Docker Compose stack
(`docker-compose/`), authenticated through Keycloak, entering only via the API
gateway on `:8082`. They assert **business outcomes** (balances, transaction
records), not service internals.

**Where:** a dedicated Gradle module `e2e-tests/` at repository root (plain Java
21, JUnit 5, REST Assured, AssertJ; no Spring). It is not a Spring Boot
application and is **not** in the per-PR CI matrix.

**How it runs:**

1. Build the JARs first. Every service `Dockerfile` is runtime-only — it does
   `ADD build/libs/<module>-0.0.1-SNAPSHOT.jar app.jar` and never runs Gradle —
   so on a clean checkout `docker compose --build` fails at that `ADD`. Run
   `./gradlew bootJar -x test` in each of the seven modules (a small
   `scripts/build-all.sh` loop, or a matrix step in CI) before touching Compose.
2. Build the stack **from the checkout**, not from published images:
   `docker-compose/docker-compose.yml` declares the Java services with `image:`
   only, so `docker compose up --build` would test whatever is on Docker Hub. An
   e2e override `docker-compose/docker-compose.e2e.yml` adds a `build:` context
   (`../<module>`) for each of the seven `javatodev/*` service images; run
   `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build`.
   (Alternative: convert the Dockerfiles to multi-stage builds that run `bootJar`
   themselves; then step 1 disappears. Not chosen initially — it slows every
   image build and changes the published-image Dockerfiles.)
3. Wait for readiness by **name**, not by count: poll `GET http://localhost:8081/eureka/apps`
   until `CORE-BANKING-SERVICE`, `INTERNET-BANKING-API-GATEWAY`,
   `INTERNET-BANKING-USER-SERVICE`, `INTERNET-BANKING-FUND-TRANSFER-SERVICE` and
   `INTERNET-BANKING-UTILITY-PAYMENT-SERVICE` are all `UP`, and Keycloak answers on
   `:8080`. The suite's `@BeforeAll` repeats this check and fails fast with the
   missing names.
4. `cd e2e-tests && ./gradlew test -Dgateway.url=http://localhost:8082 -Dkeycloak.url=http://localhost:8080`
5. `docker compose -f docker-compose.yml -f docker-compose.e2e.yml down` — always,
   including on failure (`if: always()` in CI).

The test module never starts containers itself (a `ComposeContainer` would make
the suite own a ~2 minute startup and hide image-vs-source ambiguity); the stack
is a precondition and its URLs are system properties with local defaults.

**Conventions:**

- Class suffix `E2E`, `@Tag("e2e")`; one class per business flow:
  `AuthenticationE2E`, `FundTransferE2E`, `UtilityPaymentE2E`, `UserLifecycleE2E`.
- Token via password grant at `/realms/javatodev-internet-banking/protocol/openid-connect/token`,
  client `javatodev-internet-banking-api-client`, secret read from
  `docker-compose/keycloak/realm-export.json` (300 s token lifetime — fetch once
  per class, refresh if a class runs long); mechanics are in the
  `banking-stack-testing` skill. Credentials come from the **required**
  `E2E_USERNAME` / `E2E_PASSWORD` env vars — the suite fails fast when they are
  unset instead of falling back to the seeded admin account. Locally, export the
  seeded test user from the README; in CI, provide them as repository secrets.
  Never commit tokens or log them.
- **Read before mutate:** every money test reads the source and destination
  balances first and asserts `before - amount == after` — never a hard-coded
  expected balance, because the compose volume persists between runs.
- **Which balance:** assert `actualBalance` exactly. `availableBalance` is a known
  divergence — `TransactionService` sets it to `actualBalance - amount` *after*
  `actualBalance` was already reduced, so it drops by `2 × amount`. The e2e test
  asserts the intended `before - amount` on both fields and is expected to fail
  on `availableBalance` until core is fixed; do not weaken the assertion.
- Assert both the response (`200`, `transactionId`) **and** the effect: account
  balances via `/banking-core/api/v1/account/bank-account/{number}`; the transfer
  record by paging `GET /fund-transfer/api/v1/transfer` (it only takes `Pageable`,
  there is no filter) sorted by id desc and locating the row by
  `transactionReference`; the payment record likewise via
  `GET /utility-payment/api/v1/utility-payment` by `referenceNumber` + account +
  amount (the list DTO carries no core `transactionId`).
- Security smoke: protected `GET` without `Authorization` returns `401`.
- Tests are independent and idempotent-enough to rerun on the same stack: use
  unique `referenceNumber`s (`UUID`) and small amounts.

The Postman collection in `postman_collection/` stays as the **manual
exploration** tool; it is not the automated e2e suite and is not run in CI.
