# Integration tests (`./gradlew integrationTest`)

Part of the [testing strategy](../../TESTING.md).

**Definition:** the Spring context of **one** module against real MySQL in a
container (Flyway where the module has migrations), real HTTP in/out, with every
*other* service replaced by WireMock (Keycloak excepted — see the user-service
row). No Eureka, no Config Server, no Zipkin. Two shapes, both `@Tag("integration")`:

## Repositories — `@DataJpaTest`

Only for repositories with **custom queries** (derived `findBy...`, `@Query`,
ordering, pagination, date ranges). Plain `JpaRepository` CRUD is not tested.

- Class `<Repository>IT`, `@DataJpaTest`, `@ActiveProfiles("integration")`,
  `@AutoConfigureTestDatabase(replace = NONE)`,
  `@Import(MySqlTestcontainerConfig.class)` ([below](#full-module--springboottest)).
  The profile is not optional: the base `application.yml` has
  `spring.flyway.enabled=false` and `ddl-auto: none`, and `@ServiceConnection`
  only supplies datasource coordinates, so without `integration` the slice hits
  an empty MySQL schema and every query fails on a missing table. A slice cannot share the
  `@SpringBootTest` context (different configuration → different cache key), so a
  module with both shapes runs **two** contexts; the container is still one
  because `MySqlTestcontainerConfig` hands out a JVM singleton (below). This is
  what catches dialect drift; H2 hides it.
- H2 (`spring.flyway.enabled=false`, `ddl-auto: none`, the current
  `src/test/resources/application.yml`) is kept **only** for the `contextLoads`
  smoke tests so `./gradlew test` never requires Docker. Remove
  `database-platform: org.hibernate.dialect.H2Dialect` from that base file (it
  would otherwise be applied to the MySQL container too) and let Hibernate detect
  the dialect.
- Seed data per test through `TestEntityManager`, not through shared SQL scripts,
  except for core's Flyway `temp_data` migration which tests may rely on for
  read-only cases.

## Full module — `@SpringBootTest`

**Location & naming:** same `src/test/java` tree, class suffix `IT`
(`FundTransferIT`), annotated `@Tag("integration")`. Gradle wiring (to be added
in every module's `build.gradle`):

```groovy
test {
    useJUnitPlatform { excludeTags 'integration' }
    finalizedBy jacocoTestReport
}

tasks.register('integrationTest', Test) {
    description = 'Spring Boot integration tests (Testcontainers + WireMock)'
    group = 'verification'
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    useJUnitPlatform { includeTags 'integration' }
    shouldRunAfter test
}
```

`integrationTest` is deliberately **not** wired into `check`: `./gradlew build`
must stay Docker-free like `test`. CI runs `integrationTest` explicitly ([CI](ci.md#ci)).
Because `jacocoTestReport` only reads `test.exec`, integration coverage is
excluded from the report unless the module adds
`jacocoTestReport { executionData fileTree(layout.buildDirectory).include("jacoco/*.exec") }`
and `integrationTest { finalizedBy jacocoTestReport }` — do that only once the
threshold in [CI](ci.md#ci) is enabled.

**Infrastructure is context-scoped, not class-scoped.** JUnit's `@Testcontainers`
/ `@Container static` and `@RegisterExtension static` start and stop per *test
class*; with `@DynamicPropertySource` that means every class hands Spring a new
JDBC URL / WireMock port, so the context cache misses and each `IT` class pays a
full MySQL + context start. Instead, containers are Spring beans and WireMock is a
JVM singleton:

```java
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainerConfig {
    // JVM singleton so the @DataJpaTest and @SpringBootTest contexts (two cache
    // entries) share one MySQL. Database name MUST equal the production schema:
    // core's seed migration uses schema-qualified names
    // (INSERT INTO banking_core_service.banking_core_user ...) and fails with
    // "Unknown database" against Testcontainers' default `test`.
    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>("mysql:8.4").withDatabaseName("banking_core_service");

    @Bean @ServiceConnection
    MySQLContainer<?> mysql() { return MYSQL; }   // Boot starts it once; start() is idempotent
}

public final class CoreBankingStub {                       // one per downstream, one per JVM
    public static final WireMockServer SERVER = new WireMockServer(
        wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock/core-banking-service"));
    static { SERVER.start(); }
}

@Tag("integration")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@Import(MySqlTestcontainerConfig.class)
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.core-banking-service.url",
                     CoreBankingStub.SERVER::baseUrl);
    }

    @BeforeEach void resetStubs() { CoreBankingStub.SERVER.resetAll(); }
}
```

Boot ≥ 3.1 starts a `@ServiceConnection` container bean with the context and
stops it when the context closes — for cached contexts that is JVM exit, so one
MySQL per module run even with two contexts (the caveat: if the context cache
ever evicts — default limit 32 — the shared container is stopped under the other
context; keep the number of distinct configurations to two). The
database name is the module's production schema (`banking_core_service`,
`banking_core_user_service`, `banking_core_fund_transfer_service`,
`banking_core_utility_payment_service` — `docker-compose/mysql/privileges.sql`).

**Feign → WireMock needs no production change.** Spring Cloud OpenFeign 4.1
resolves `spring.cloud.openfeign.client.config.<name>.url` when the `@FeignClient`
has no `url` attribute and only falls back to load-balancing when neither is set
(`FeignClientFactoryBean#getTarget`). Keep the annotations as they are
(`name`/`value = "core-banking-service"`) and set that property from
`@DynamicPropertySource` only.

**Bootstrap context.** The four servlet services and the gateway use
`spring-cloud-starter-bootstrap` with a `bootstrap.yml` pointing at the config
server on `:8090`. The bootstrap context reads `bootstrap*.yml`, *not*
`application-integration.yml`, so disabling the config client there has no
effect. Every module gets a `src/test/resources/bootstrap.yml` with
`spring.cloud.config.enabled: false` / `fail-fast: false` — exactly what
`internet-banking-api-gateway/src/test/resources/bootstrap.yml` already does.

**Profile per module**, `src/test/resources/application-integration.yml`. Common:
`eureka.client.enabled=false`, `spring.cloud.discovery.enabled=false`,
`management.tracing.enabled=false`, plus every runtime property the module
normally receives from the external config repo (see [TESTING.md §1](../../TESTING.md#1-test-pyramid)). Persistence differs
because **only `core-banking-service` has Flyway and migrations**; user,
fund-transfer and utility-payment have no `flyway` dependency, no `db/migration`,
and get their schema from `ddl-auto: update` in the external config:

| Module | `spring.flyway.enabled` | `spring.jpa.hibernate.ddl-auto` | Migration fidelity |
|---|---|---|---|
| `core-banking-service` | `true` | `validate` | real — migrations + entity/DDL drift both caught |
| `user`, `fund-transfer`, `utility-payment` | `false` | `create-drop` | none today; adopting Flyway in these three modules is a prerequisite |

Per-module responsibilities (assertions describe the **current** code; intended
changes are marked):

| Module | Integration tests prove |
|---|---|
| `core-banking-service` | Migrations apply on MySQL and `validate` passes against the entities; `POST /api/v1/transaction/fund-transfer` debits/credits both `banking_core_account` rows and writes two `banking_core_transaction` rows (negated debit) in one transaction; `InsufficientFunds` leaves balances unchanged. |
| `fund-transfer-service`, `utility-payment-service` | `PENDING` (`PROCESSING` for payments) row is saved, core is called once with the right body (`WireMock.verify`), row moves to `SUCCESS` with the returned reference. **Failure path (current code):** on a core 4xx/5xx the `FeignException` propagates through `GlobalExceptionHandler#handleException` as HTTP `400` with a string body and the row **stays `PENDING`/`PROCESSING`** — there is no `FAILED` transition and no `CustomFeignErrorDecoder` in these two modules. Neither service method is `@Transactional`, so the `PENDING`/`PROCESSING` row is committed by the first `save` before the remote call and survives the failure; pin that the row exists, is not `SUCCESS`, and core was called exactly once. A `FAILED` status is a product change to implement first, then assert. |
| `user-service` | Real Keycloak via `dasniko/testcontainers-keycloak` importing `docker-compose/keycloak/realm-export.json` (it already contains the `javatodev-internet-banking-kc-api-client` service account the code uses); core-banking via WireMock. The `integration` profile must override the Keycloak values in the existing `src/test/resources/application.yml`, which are wrong for the compose realm: `app.config.keycloak.server-url` (base file has `http://localhost:8080/auth`; the Keycloak 23 image has no `/auth` prefix) becomes the container's `getAuthServerUrl()` via `@DynamicPropertySource`, and `clientId` (base file `internet-banking-api-client`, not in the realm export) / `client-secret` become `javatodev-internet-banking-kc-api-client` and its secret from `realm-export.json` — `KeycloakProperties` builds the static admin client from these on first use. User is created in Keycloak and locally with `authId`; duplicate registration maps to `UserAlreadyRegisteredException`; core `400` is mapped by `CustomFeignErrorDecoder` into an `ErrorResponse`. WireMock is *not* used for Keycloak: `KeycloakProperties` builds a **static** admin client (`client_credentials` token endpoint + stateful search/create/get/update calls), which is impractical to stub and would pin the first port for the JVM. |
| `api-gateway` | The gateway has **no routes in this repo** — they are `lb://` routes in the external config. The IT defines a test-local copy of the four routes (`/user/**`, `/fund-transfer/**`, `/banking-core/**`, `/utility-payment/**`, `StripPrefix=1` — the prefixes in the published config; the Postman collection's `/core/**` and `/payment/**` are stale, see the `banking-stack-testing` skill) with `uri: http://localhost:${wiremock.port}` (drift against the external file is an accepted risk, reviewed on config changes) — or keeps `lb://` and declares `spring.cloud.discovery.client.simple.instances.<service>[0].uri`. `WebTestClient` + `mockJwt().jwt(j -> j.subject("…"))`: forwarded request carries `X-Auth-Id == subject` (`GatewayConfiguration` uses `Principal::getName`; anonymous requests get the literal `SYSTEM USER`); no token → `401`. Fix `src/test/resources/application.yml` JWK realm from `javatodev` to `javatodev-internet-banking`. |
| `config-server`, `service-registry` | `contextLoads` only. |

**Context caching.** Keep the integration configuration identical across test
classes (same base class, same profile, same imported `@TestConfiguration`, no
per-class `@MockBean`/`@TestPropertySource`) so Spring starts **one**
`@SpringBootTest` context per module (plus one `@DataJpaTest` context where the
module has repository tests). Test-level isolation: `CoreBankingStub.SERVER.resetAll()` in
`@BeforeEach`, `@Sql(scripts = "/sql/clean.sql", executionPhase = BEFORE_TEST_METHOD)`
for data, or `@Transactional` on the test class — but not for tests of
`TransactionService` (which is `@Transactional` and whose atomicity is the point)
nor of `FundTransferService`/`UtilityPaymentService` (which are **not**: each
`save` commits on its own, and a test-level transaction would hide the
committed-`PENDING`-then-remote-call sequence asserted above).

## WireMock stubs

Stub files live under `src/test/resources/wiremock/<downstream-service>/` using
WireMock's root layout — `mappings/*.json` for stubs and `__files/` for response
bodies — and each `WireMockServer` is created with
`usingFilesUnderClasspath("wiremock/<downstream-service>")` so the mappings are
loaded at startup (one server per downstream). The same core-banking mapping
files are copied verbatim across fund-transfer, utility-payment and user
services and use the shared fixture account numbers ([test data](unit-tests.md#test-data)). Inline `stubFor(...)`
is fine for per-test error cases; `resetAll()` restores the file mappings.

## Docker requirement

`integrationTest` requires a Docker daemon (Testcontainers). `./gradlew test` and
`./gradlew build` never do. Developers without Docker still get unit + slice +
smoke coverage locally; CI runs both tasks. `testcontainers.reuse.enable=true`
(`~/.testcontainers.properties`) is fine locally, never in CI.

## Contract tests (future)

Fund-transfer, utility-payment and user services each carry their own copy of
core-banking DTOs (`AccountResponse`, `FundTransferResponse`). The WireMock stub
files above are the *de facto* contract. If the provider contract keeps drifting,
graduate those stubs to Spring Cloud Contract (provider-side verification in
`core-banking-service`, published stubs consumed by the others). Not adopted now.
