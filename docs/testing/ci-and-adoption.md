# CI, feature checklist and adoption order

Part of the [testing strategy](../../TESTING.md).

## CI

`.github/workflows/ci.yml` (per-module matrix, already exists) is extended, not
replaced:

| Job | Trigger | Command |
|---|---|---|
| `test` (matrix) | every push/PR | `./gradlew test` — unit + slice + smoke, JaCoCo report (current). |
| `integration-test` (matrix) — *to be added* | every push/PR, after `test` | `./gradlew integrationTest` — ubuntu-latest has Docker for Testcontainers. |
| `e2e` — *to be added* | nightly `schedule` + `workflow_dispatch` + release tags | `bootJar` in all seven modules → compose up with the e2e override (built from the checkout) → `e2e-tests` → `compose down -v` (`if: always()`, fresh seed every run); upload REST Assured logs on failure. |

Until the two new jobs exist, only `./gradlew test` is enforced; the integration
and e2e layers are the target state described in [Adoption order](#adoption-order).

Cost and gating: `integration-test` adds one `mysql:8.4` pull + start per module
for the four persistence modules plus a Keycloak container for user-service
(≈ 1–2 min each on `ubuntu-latest`); `e2e` adds seven `bootJar`s and an
11-container stack (≈ 5–8 min). Use `gradle/actions/setup-gradle` caching in both
jobs, keep `integration-test` per-PR but `e2e` on `main`/nightly only, and
re-evaluate once wall time exceeds ~10 min per PR.

Coverage thresholds (`jacocoTestCoverageVerification`) are **not** enforced yet.
Once the `service/` packages are covered by tests following this document, add a
`minimum = 0.80` line-coverage rule scoped to `com.javatodev.finance.service.*`
per module and let CI fail on regressions.

## Checklist for a new or changed feature

1. Unit tests for every new/changed `service/` method: happy path, each error
   path (`assertThatThrownBy`), boundaries (inclusive ranges, zero/negative
   amounts, empty results).
2. `@WebMvcTest` case for every new endpoint or changed status/error mapping.
3. `@DataJpaTest` (MySQL Testcontainers, `@Tag("integration")`) for every new repository query.
4. One `IT` per new cross-service interaction or migration; in core, a new
   migration must keep `ddl-auto: validate` green.
5. Extend the relevant `E2E` flow only if the change is visible through the gateway.
6. `./gradlew test` green in the module. `./gradlew integrationTest` green as
   well once the module has the task (phase 1/2 below); until then items 3–4 do
   not apply to that module — today no module defines `integrationTest`, so the
   enforced gate is `test` alone.

## Adoption order

This document is the target state. Rollout is **infrastructure first, then one
owner per service, bottom-up inside each service** — not "all unit tests first"
and not one owner per layer. Each module is an independent Gradle build with its
own CI job, so service owners never block each other; the shared infrastructure
is built once in a pilot so four owners do not invent four variants.

### Phase 1 — foundation in `core-banking-service` (one PR, the reference)

Core is the pilot because it is the simplest place to prove the infrastructure:
Flyway migrations, no Feign, no Keycloak.

1. `integrationTest` task, Testcontainers / WireMock deps, `src/test/resources/bootstrap.yml`,
   `application-integration.yml`, drop `H2Dialect` from the base test yml.
2. `MySqlTestcontainerConfig`, `AbstractIntegrationTest`, `fixture/` package
   (`CoreBankingFixtures` with the seeded account numbers).
3. Exactly one exemplar per layer: convert `TransactionServiceTest` to AssertJ,
   `TransactionControllerTest` (`@WebMvcTest`), `TransactionRepositoryIT`
   (`@DataJpaTest`), `FundTransferIT` (`@SpringBootTest`).
4. `integration-test` CI matrix job (core only at first; other modules join as
   they land the task).

Everything after this copies phase 1; review it hardest.

### Phase 2 — fan out, one owner per service (parallel)

`user-service`, `fund-transfer-service`, `utility-payment-service`,
`api-gateway`. Inside a service go bottom-up so the cheap layers stabilise first:

1. Unit tests for every `service/` method (happy, error, boundary).
2. `@WebMvcTest` per controller (with the `AppAuthUserFilter` slice setup).
3. Copy the phase-1 infrastructure; `@DataJpaTest` for custom queries; one
   `@SpringBootTest` IT per cross-service interaction — WireMock stubs for core
   under `src/test/resources/wiremock/core-banking-service/`, Keycloak
   Testcontainer in user-service, test-local routes + `mockJwt()` in the gateway.
4. Join the `integration-test` matrix.

The three satellite persistence services run `ddl-auto: create-drop` until they
adopt Flyway — do that adoption before relying on them for migration fidelity.
Remaining core controllers/services are finished by the core owner in this phase.

### Phase 3 — e2e (one owner; can start alongside phase 2)

`e2e-tests` module, `docker-compose.e2e.yml` build override, nightly workflow.
Depends only on the running stack, not on the ITs. Keep it to the money and auth
flows (`AuthenticationE2E`, `FundTransferE2E`, `UtilityPaymentE2E`,
`UserLifecycleE2E`).

### Phase 4 — gates

Enable JaCoCo thresholds (`service/` ≥ 80 % per module) and merge
`integrationTest` coverage ([integration tests](integration-tests.md#full-module--springboottest)).

**Definition of done per service:** `service/` coverage ≥ 80 %, `./gradlew test`
still Docker-free, `integrationTest` green in CI, all new tests following the
layer pages of this strategy.
