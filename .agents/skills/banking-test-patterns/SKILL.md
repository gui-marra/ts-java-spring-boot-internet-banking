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
| a custom repository query | `@DataJpaTest` on MySQL Testcontainers, Flyway on, `@Tag("integration")` | `<Repository>IT` | `./gradlew integrationTest` |
| wiring of one whole module (migrations, Feign, filters, error mapping) | `@SpringBootTest` extending `AbstractIntegrationTest`, `@Tag("integration")` | `<Feature>IT` | `./gradlew integrationTest` |
| a money flow through the gateway across services | REST Assured test in `e2e-tests/`, `@Tag("e2e")` | `<Flow>E2E` | nightly / manual, stack already up |

## Frameworks (all via `spring-boot-starter-test` unless noted)

JUnit 5 only; **AssertJ** for assertions in new tests; **Mockito** with
`@ExtendWith(MockitoExtension.class)` (strict stubs) or `mock(X.class)` in
`@BeforeEach` for lenient shared setup; **Testcontainers** `mysql:8.4` +
`@ServiceConnection`; **WireMock** for any other service's HTTP;
**REST Assured** only in `e2e-tests`. Never add Spock, Cucumber, PowerMock,
random-data generators, or JUnit 4.

## Mocking rules

- Mock ports: `*Repository`, Feign clients (`BankingCoreFeignClient`,
  `BankingCoreRestClient`), `KeycloakManager`/`KeycloakUserService`, injected `Clock`.
- Never mock entities, DTOs, mappers, enums, statics, or Lombok code.
- Construct services through their `@RequiredArgsConstructor` constructor.
- Assert via outputs and `ArgumentCaptor`, not `verify` counts alone;
  `verifyNoInteractions` only for negative cases.
- `@MockBean` only in `@WebMvcTest` slices. In integration tests replace HTTP
  with WireMock instead so the Spring context stays cacheable.
- Money: `BigDecimal.valueOf`, `isEqualByComparingTo`.

## Fixtures

`src/test/java/com/javatodev/finance/fixture/*Fixtures.java` — deterministic,
fully valid objects with overridable fields (`anAccount("A1", 200)`,
`aTransfer("A1", "A2", 100)`).

## Integration test environment

Profile `integration` (`src/test/resources/application-integration.yml`):
Eureka, Config Server, tracing off; Flyway on; Feign `url` pointed at WireMock
via `clients.<service>.url` set in `@DynamicPropertySource`. WireMock mappings
live in `src/test/resources/wiremock/<downstream>/mappings/*.json`, loaded with
`usingFilesUnderClasspath("wiremock/<downstream>")`. The `integrationTest` Gradle
task must set `testClassesDirs`/`classpath` from `sourceSets.test`. Requires
Docker; `./gradlew test` must keep passing without Docker (H2 smoke config stays).
The `integrationTest` CI job does not exist yet — see TESTING.md §9.

## E2E

Stack is a precondition, built from the checkout with the
`docker-compose.e2e.yml` build override (see `banking-stack-testing` skill);
tests never start containers. Wait for the five Java apps to be `UP` in Eureka
by name. Token via Keycloak password grant, secret read from
`docker-compose/keycloak/realm-export.json`, user from required `E2E_USERNAME` /
`E2E_PASSWORD` (no defaults). Read balances before mutating and assert
`before - amount == after`; unique `referenceNumber` per run.

## Done means

Unit tests for every changed `service/` method (happy, each error, boundaries),
`@WebMvcTest` for every changed endpoint, `@DataJpaTest` for every new query,
one `IT` per new cross-service interaction or migration, and both
`./gradlew test` and `./gradlew integrationTest` green in the module.
