---
name: banking-test-patterns
description: >-
  Test conventions for every module of ts-java-spring-boot-internet-banking:
  which layer a test belongs to (unit / slice / integration / e2e), the
  frameworks to use (JUnit 5, AssertJ, Mockito, Spring slices, Testcontainers,
  WireMock, REST Assured) and the mocking rules. Use whenever writing or
  reviewing tests in this repo; the full definition lives in TESTING.md.
---

# Skill: Write tests the repo way

`TESTING.md` at the repository root is the source of truth. This skill is the
short form for agents; when in doubt, read the relevant section there.

## Pick the layer first

| If the test proves... | Write a... | Named | Runs in |
|---|---|---|---|
| a business rule in `service/` | plain JUnit 5 + Mockito unit test, no Spring | `<Class>Test` | `./gradlew test` |
| an HTTP contract of a controller | `@WebMvcTest(<Controller>.class)` + `MockMvc` + `@MockBean` service | `<Controller>Test` | `./gradlew test` |
| a custom repository query | `@DataJpaTest` + `@Import(MySqlTestcontainerConfig.class)`, `@Tag("integration")` | `<Repository>IT` | `./gradlew integrationTest` |
| wiring of one whole module (migrations, Feign, filters, error mapping) | `@SpringBootTest` extending `AbstractIntegrationTest`, `@Tag("integration")` | `<Feature>IT` | `./gradlew integrationTest` |
| a money flow through the gateway across services | REST Assured test in `e2e-tests/`, `@Tag("e2e")` | `<Flow>E2E` | nightly / manual, stack already up |

## Frameworks (all via `spring-boot-starter-test` unless noted)

JUnit 5 only; **AssertJ** for assertions in new tests; **Mockito** with
`@ExtendWith(MockitoExtension.class)` (strict stubs) or `mock(X.class)` in
`@BeforeEach` for lenient shared setup; **Testcontainers** `mysql:8.4` as a
`@Bean @ServiceConnection` in a `@TestConfiguration`; **WireMock** (pinned, not in
the Boot BOM) for any other service's HTTP; **Keycloak Testcontainer**
(`dasniko/testcontainers-keycloak`, pinned) in user-service only; **REST Assured**
only in `e2e-tests`. Never add Spock, Cucumber, PowerMock, random-data
generators, or JUnit 4. `@MockBean` now, `@MockitoBean` after the Boot 3.4 upgrade.

## Mocking rules

- Mock ports: `*Repository`, Feign clients (`BankingCoreFeignClient`,
  `BankingCoreRestClient`), `KeycloakManager`/`KeycloakUserService`, injected `Clock`.
- Never mock entities, DTOs, mappers, enums, statics, or Lombok code.
- Construct services through their `@RequiredArgsConstructor` constructor.
- Assert via outputs and `ArgumentCaptor`, not `verify` counts alone;
  `verifyNoInteractions` only for negative cases.
- `@MockBean` only in `@WebMvcTest` slices. In integration tests replace HTTP
  with WireMock instead so the Spring context stays cacheable.
- Money: `BigDecimal.valueOf`, `isEqualByComparingTo` / `usingComparatorForType`.
- Exceptions expose `code` (`.extracting("code")`), not `errorCode`.
- Ids are `UUID.randomUUID()` and timestamps come from auditing: assert with
  `matches(UUID_REGEX)` / `isCloseTo`, or add a `Supplier`/`Clock` seam.
- Mappers are `BaseMapper` + `BeanUtils.copyProperties`: instantiate directly, no mocks.
- `AppAuthUserFilter` in a `@WebMvcTest`: register via a `@TestConfiguration`
  `FilterRegistrationBean` (not `@Import(AuditConfig)`); read
  `ApiRequestContextHolder` inside a `doAnswer` on the mocked service — it is a
  `ThreadLocal` cleared after the request, not a method argument.

## Fixtures

`src/test/java/com/javatodev/finance/fixture/*Fixtures.java` — deterministic,
fully valid objects with overridable fields (`anAccount("A1", 200)`,
`aTransfer("A1", "A2", 100)`). Cross-service ids (accounts `100015003000/1`,
utility providers) come from core's `temp_data` migration and are the only ones
used in WireMock mappings and e2e.

## Integration test environment

- `src/test/resources/bootstrap.yml` with `spring.cloud.config.enabled: false`
  (the bootstrap context ignores `application-*.yml`; gateway already has this).
- Profile `integration` (`application-integration.yml`): Eureka, discovery,
  tracing off, plus every property the module normally gets from the external
  config repo. Core: Flyway on + `ddl-auto: validate`. User / fund-transfer /
  utility-payment have **no Flyway**: `flyway.enabled=false`, `ddl-auto: create-drop`.
- MySQL container: `withDatabaseName(<production schema>)` — core's seed
  migration is schema-qualified (`banking_core_service.…`) and fails on the
  default `test` db. Container is a JVM-singleton handed out by a `@Bean @ServiceConnection` (shared by the `@DataJpaTest` and `@SpringBootTest` contexts), not `@Container static`.
- WireMock: one JVM-singleton `WireMockServer` per downstream, `resetAll()` in
  `@BeforeEach`; mappings in `src/test/resources/wiremock/<downstream>/mappings/*.json`
  via `usingFilesUnderClasspath`. Feign → WireMock through
  `spring.cloud.openfeign.client.config.core-banking-service.url` in
  `@DynamicPropertySource`; **no `url` attribute on the `@FeignClient`**.
- Gateway routes are not in this repo (external config, `lb://`): the IT declares
  test-local `spring.cloud.gateway.routes` to WireMock.
- Failure paths: fund-transfer/utility-payment have no `FAILED` state and no Feign
  error decoder, and no `@Transactional` — assert the committed row stays `PENDING`/`PROCESSING` and a `400`.
- Gradle: `integrationTest` sets `testClassesDirs`/`classpath` from
  `sourceSets.test`; **not** wired into `check`. Requires Docker; `./gradlew test`
  and `build` must keep passing without Docker (H2 smoke config stays, drop
  `H2Dialect` from the base test yml). The CI job does not exist yet — TESTING.md §9.

## E2E

Stack is a precondition, built from the checkout: run `./gradlew bootJar -x test`
in all seven modules first (Dockerfiles only `ADD build/libs/*.jar`), then
`up -d --build` with the `docker-compose.e2e.yml` build override (see
`banking-stack-testing` skill); tests never start containers. Wait for the five Java apps to be `UP` in Eureka
by name. Token via Keycloak password grant, secret read from
`docker-compose/keycloak/realm-export.json`, user from required `E2E_USERNAME` /
`E2E_PASSWORD` (no defaults). Read balances before mutating and assert
`before - amount == after` on `actualBalance` (`availableBalance` is double-
subtracted by core today — assert intended, expect red until fixed); locate
records by paging the list endpoints (no filters exist); unique `referenceNumber`
per run.

## Done means

Unit tests for every changed `service/` method (happy, each error, boundaries),
`@WebMvcTest` for every changed endpoint, `@DataJpaTest` for every new query,
one `IT` per new cross-service interaction or migration, and both
`./gradlew test` and `./gradlew integrationTest` green in the module.
