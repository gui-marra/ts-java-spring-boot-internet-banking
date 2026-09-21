# CI and feature checklist

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
and e2e layers are target state.

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
   well once the module has the task; until then items 3–4 do not apply to that
   module — today no module defines `integrationTest`, so the enforced gate is
   `test` alone.
