# Testing Strategy

Canonical definition of how the services in this repository are tested. Every
module (`core-banking-service`, `internet-banking-*`) follows the same layers,
frameworks, and conventions so that a test written in one service reads the same
in any other. This document defines the patterns; it does not require any
existing test to be rewritten immediately — new and touched tests must follow it.

This page holds what is common to every layer (the pyramid and the framework
choices). Each layer has its own page under `docs/testing/`:

| Page | Covers |
|---|---|
| [Unit tests](docs/testing/unit-tests.md) | naming, structure, mocking rules, test data & fixtures |
| [Slice tests](docs/testing/slice-tests.md) | `@WebMvcTest` controller contracts, `AppAuthUserFilter` in a slice |
| [Integration tests](docs/testing/integration-tests.md) | `integrationTest` task, Testcontainers MySQL, WireMock, bootstrap/profile setup, per-module responsibilities, contract tests |
| [End-to-end tests](docs/testing/e2e-tests.md) | `e2e-tests` module, compose stack, auth, balance assertions |
| [CI & checklist](docs/testing/ci.md) | CI jobs, definition of done for a feature |

Short form for agents: `.agents/skills/banking-test-patterns/SKILL.md`.

## 1. Test pyramid

| Layer | Purpose | Scope | Runs on | Speed |
|---|---|---|---|---|
| **Unit** | Business rules in `service/`, mappers, exception mapping | One class, collaborators mocked, no Spring context | Every PR (`./gradlew test`) | ms |
| **Slice** | Controller contract (`@WebMvcTest`) | One Spring layer, rest mocked | Every PR (`./gradlew test`) | < 1 s each |
| **Integration** | JPA queries on MySQL (`@DataJpaTest`); one service wired end-to-end against real infra (MySQL, downstream HTTP) | `@DataJpaTest` or full `@SpringBootTest` for **one** module | Every PR (`./gradlew integrationTest`) — *target state; CI job not yet added, see [CI](docs/testing/ci.md)* | seconds |
| **End-to-end** | Business flows through the gateway across services | Whole Docker Compose stack | Nightly / manual / pre-release — *target state* | minutes |

`./gradlew test` must never require Docker; anything that needs Testcontainers is
tagged `integration` and runs under `integrationTest`.

Rule of thumb: push a test as far *down* the pyramid as it can go and still prove
the behaviour. Most tests are unit tests; integration tests cover wiring
(migrations, JSON, filters, Feign); e2e tests cover only the money-moving flows.

Tests assert **intended** behaviour. Where the current code is known to diverge
(see [e2e](docs/testing/e2e-tests.md) `availableBalance`, [integration](docs/testing/integration-tests.md) failure paths) the document says so, and the test
is written against the intended behaviour and fixed *with* the code — never
adjusted to bless a bug.

**The external configuration repo is part of the system under test.** Datasources,
gateway routes (`lb://…`), Keycloak URLs and client secret, `ddl-auto` and ports
all come from `JavatoDev-com/internet-banking-microservices-configurations`
(`internet-banking-config-server/src/main/resources/application.yml`), not from
this repo. Integration tests therefore replicate the properties a module needs in
their own `src/test/resources` ([integration tests](docs/testing/integration-tests.md)), and the e2e stack — even when built from the
checkout — still pulls runtime config from that repo's `main` at start-up.

Coverage is measured with JaCoCo (already configured in every module). The
`service/` package of each module is the coverage target; controllers, DTOs,
entities, mappers and configuration are exercised through slice/integration tests
and are not chased for coverage numbers.

## 2. Frameworks (all layers)

All of these are provided by `spring-boot-starter-test` unless noted. Versions are
managed by the Spring Boot BOM (`3.2.x`) where the BOM has them (JUnit, AssertJ,
Mockito, Testcontainers, REST Assured); WireMock and the Keycloak Testcontainer
are **not** in the BOM and are pinned explicitly, identically, in every module.

| Concern | Library | Notes |
|---|---|---|
| Test runner | **JUnit 5 (Jupiter)** | `useJUnitPlatform()` is already enabled. No JUnit 4, no Vintage. |
| Assertions | **AssertJ** | `assertThat(...)` fluent API for all new tests. JUnit `Assertions.*` are allowed only in existing tests until touched. |
| Mocking | **Mockito** (`mockito-core`, `mockito-junit-jupiter`) | See [unit tests](docs/testing/unit-tests.md#mocking-rules). No PowerMock, no static mocking of production code. |
| Spring test support | `spring-boot-test`, `spring-test` | `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`, `MockMvc`, `WebTestClient`. |
| Security in tests | `spring-security-test` | Gateway only (`mockJwt()` / `SecurityMockServerConfigurers`). |
| JSON assertions | JsonPath (bundled) + `JSONassert` | `jsonPath("$.x")` in MockMvc; `JSONAssert.assertEquals` for full-body contracts. |
| Real databases | **Testcontainers** (`org.testcontainers:mysql`, `junit-jupiter`) + `spring-boot-testcontainers` | Integration tests that must run against MySQL (dialect fidelity everywhere; Flyway fidelity in `core-banking-service`, the only module with migrations). |
| Real Keycloak | **Keycloak Testcontainer** (`com.github.dasniko:testcontainers-keycloak`, pinned) | `user-service` only — imports `docker-compose/keycloak/realm-export.json`; see [integration tests](docs/testing/integration-tests.md). |
| HTTP stubbing | **WireMock** (`org.wiremock:wiremock-standalone`, pinned) | Stubs `core-banking-service` for Feign-based services and downstream routes for the gateway. |
| HTTP client for e2e | **REST Assured** (`io.rest-assured:rest-assured`) | Only in the `e2e-tests` module. |
| Test data | Lombok `@Builder` on entities + hand-written `*Fixtures` classes | No Instancio/Podam/EasyRandom — deterministic data only. |

`@MockBean` is the Boot 3.2 API; Boot 3.4 deprecates it in favour of
`@MockitoBean`. Use `@MockBean` now, migrate mechanically on the Boot upgrade.

Deliberately **not** used: Spock/Groovy (single language keeps CI and IDE setup
trivial), Cucumber (no non-developer stakeholders write scenarios), Pact/Spring
Cloud Contract (see [contract tests](docs/testing/integration-tests.md#contract-tests-future) — revisit if consumers of `core-banking-service` grow).
