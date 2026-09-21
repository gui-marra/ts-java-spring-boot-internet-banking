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
   so once the e2e override (step 2) adds build contexts, `docker compose --build`
   on a clean checkout fails at that `ADD` (against the base file alone the
   services are `image:`-only and `--build` is a no-op that pulls Docker Hub). Run
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
   `:8080`. Five of the seven Java apps: the registry itself
   (`register-with-eureka: false`) and the config server never appear in
   `/eureka/apps` — they are implicitly ready once the five clients have registered
   and pulled their config. The suite's `@BeforeAll` repeats this check and fails fast with the
   missing names.
4. `cd e2e-tests && ./gradlew test -Dgateway.url=http://localhost:8082 -Dkeycloak.url=http://localhost:8080`
5. `docker compose -f docker-compose.yml -f docker-compose.e2e.yml down -v` — always,
   including on failure (`if: always()` in CI). `-v` drops `mysqldata` so every
   automated run starts from core's seed migration (see *Fresh state* below);
   omit `-v` locally if you want to keep the data.

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
- **Read before mutate:** every money test reads the affected balances first. A
  transfer asserts `sourceBefore - amount == sourceAfter` and
  `destinationBefore + amount == destinationAfter`; a payment asserts
  `sourceBefore - amount == sourceAfter`. Never use hard-coded expected
  balances — the compose `mysqldata` volume persists between runs.
- **Which balance:** assert both `actualBalance` and `availableBalance` move by
  exactly `amount` (`before - amount` on the source, `before + amount` on the
  destination). Core's `TransactionService` derives each field from its own
  previous value; a `2 × amount` drop on `availableBalance` is a regression, not
  expected behaviour — do not weaken the assertion.
- Assert both the response (`200`, `transactionId`) **and** the effect: account
  balances via `/banking-core/api/v1/account/bank-account/{number}`; the transfer
  record by paging `GET /fund-transfer/api/v1/transfer?sort=id,desc` (it only
  takes `Pageable`, there is no filter, and `readAllTransfers` → `findAll(pageable)`
  applies **no default order** — the client must send `sort=id,desc` explicitly
  or the new row may sit on any page) and locating the row by
  `transactionReference`; the payment record likewise via
  `GET /utility-payment/api/v1/utility-payment` by `referenceNumber` + account +
  amount (the list DTO carries no core `transactionId`).
- Security smoke: protected `GET` without `Authorization` returns `401`.
- Tests are independent and idempotent-enough to rerun on the same stack: use
  unique `referenceNumber`s (`UUID`) and small amounts.
- **Fresh state per automated run.** Small amounts only delay exhaustion of the
  seeded `100015003000` balance, so CI tears down with `docker compose ... down -v`
  (drops `mysqldata`; core's Flyway `temp_data` migration re-seeds on the next
  `up`). A CI run therefore always starts from the seed. Locally the volume may be
  kept; run `down -v` when a money test starts failing with insufficient funds.

The Postman collection in `postman_collection/` stays as the **manual
exploration** tool; it is not the automated e2e suite and is not run in CI.
