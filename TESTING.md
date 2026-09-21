# Testing Strategy

Canonical definition of how the services in this repository are tested. Every
module (`core-banking-service`, `internet-banking-*`) follows the same layers,
frameworks, and conventions so that a test written in one service reads the same
in any other. This document defines the patterns; it does not require any
existing test to be rewritten immediately — new and touched tests must follow it.

## 1. Test pyramid

| Layer | Purpose | Scope | Runs on | Speed |
|---|---|---|---|---|
| **Unit** | Business rules in `service/`, mappers, exception mapping | One class, collaborators mocked, no Spring context | Every PR (`./gradlew test`) | ms |
| **Slice** | Controller contract (`@WebMvcTest`) | One Spring layer, rest mocked | Every PR (`./gradlew test`) | < 1 s each |
| **Integration** | JPA queries on MySQL (`@DataJpaTest`, §5.1); one service wired end-to-end against real infra (MySQL, downstream HTTP) | `@DataJpaTest` or full `@SpringBootTest` for **one** module | Every PR (`./gradlew integrationTest`) — *target state; CI job not yet added, see §7/§9* | seconds |
| **End-to-end** | Business flows through the gateway across services | Whole Docker Compose stack | Nightly / manual / pre-release — *target state* | minutes |

`./gradlew test` must never require Docker; anything that needs Testcontainers is
tagged `integration` and runs under `integrationTest`.

Rule of thumb: push a test as far *down* the pyramid as it can go and still prove
the behaviour. Most tests are unit tests; integration tests cover wiring
(migrations, JSON, filters, Feign); e2e tests cover only the money-moving flows.

Tests assert **intended** behaviour. Where the current code is known to diverge
(see §6 `availableBalance`, §5 failure paths) the document says so, and the test
is written against the intended behaviour and fixed *with* the code — never
adjusted to bless a bug.

**The external configuration repo is part of the system under test.** Datasources,
gateway routes (`lb://…`), Keycloak URLs and client secret, `ddl-auto` and ports
all come from `JavatoDev-com/internet-banking-microservices-configurations`
(`internet-banking-config-server/src/main/resources/application.yml`), not from
this repo. Integration tests therefore replicate the properties a module needs in
their own `src/test/resources` (§5), and the e2e stack — even when built from the
checkout — still pulls runtime config from that repo's `main` at start-up.

Coverage is measured with JaCoCo (already configured in every module). The
`service/` package of each module is the coverage target; controllers, DTOs,
entities, mappers and configuration are exercised through slice/integration tests
and are not chased for coverage numbers.

## 2. Frameworks

All of these are provided by `spring-boot-starter-test` unless noted. Versions are
managed by the Spring Boot BOM (`3.2.x`) where the BOM has them (JUnit, AssertJ,
Mockito, Testcontainers, REST Assured); WireMock and the Keycloak Testcontainer
are **not** in the BOM and are pinned explicitly, identically, in every module.

| Concern | Library | Notes |
|---|---|---|
| Test runner | **JUnit 5 (Jupiter)** | `useJUnitPlatform()` is already enabled. No JUnit 4, no Vintage. |
| Assertions | **AssertJ** | `assertThat(...)` fluent API for all new tests. JUnit `Assertions.*` are allowed only in existing tests until touched. |
| Mocking | **Mockito** (`mockito-core`, `mockito-junit-jupiter`) | See §3. No PowerMock, no static mocking of production code. |
| Spring test support | `spring-boot-test`, `spring-test` | `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`, `MockMvc`, `WebTestClient`. |
| Security in tests | `spring-security-test` | Gateway only (`mockJwt()` / `SecurityMockServerConfigurers`). |
| JSON assertions | JsonPath (bundled) + `JSONassert` | `jsonPath("$.x")` in MockMvc; `JSONAssert.assertEquals` for full-body contracts. |
| Real databases | **Testcontainers** (`org.testcontainers:mysql`, `junit-jupiter`) + `spring-boot-testcontainers` | Integration tests that must run against MySQL (dialect fidelity everywhere; Flyway fidelity in `core-banking-service`, the only module with migrations). |
| Real Keycloak | **Keycloak Testcontainer** (`com.github.dasniko:testcontainers-keycloak`, pinned) | `user-service` only — imports `docker-compose/keycloak/realm-export.json`; see §5. |
| HTTP stubbing | **WireMock** (`org.wiremock:wiremock-standalone`, pinned) | Stubs `core-banking-service` for Feign-based services and downstream routes for the gateway. |
| HTTP client for e2e | **REST Assured** (`io.rest-assured:rest-assured`) | Only in the `e2e-tests` module. |
| Test data | Lombok `@Builder` on entities + hand-written `*Fixtures` classes | No Instancio/Podam/EasyRandom — deterministic data only. |

`@MockBean` is the Boot 3.2 API; Boot 3.4 deprecates it in favour of
`@MockitoBean`. Use `@MockBean` now, migrate mechanically on the Boot upgrade.

Deliberately **not** used: Spock/Groovy (single language keeps CI and IDE setup
trivial), Cucumber (no non-developer stakeholders write scenarios), Pact/Spring
Cloud Contract (see §5.5 — revisit if consumers of `core-banking-service` grow).

## 3. Unit tests

**Location & naming:** `src/test/java/<same package as the class under test>/<Class>Test.java`.
Method names: `<method>_<scenario>_<expectation>` in snake-ish camel case, e.g.
`fundTransfer_insufficientFunds_throwsInsufficientFundsException`. Existing
`fundTransfer_success` style is acceptable; prefer the three-part form for new tests.

**Structure:** Arrange / Act / Assert separated by blank lines. One behaviour per test.

**No Spring context.** Services are constructed directly via their
`@RequiredArgsConstructor` constructor — the constructor *is* the wiring contract,
so a compile error tells you when dependencies changed.

```java
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock AccountService accountService;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock TransactionRepository transactionRepository;
    @InjectMocks TransactionService transactionService;

    @Test
    void fundTransfer_sufficientBalance_debitsSourceAndCreditsTarget() {
        // Arrange
        when(accountService.readBankAccount("A1")).thenReturn(aBankAccount("A1", 200));
        when(accountService.readBankAccount("A2")).thenReturn(aBankAccount("A2", 50));
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(anAccountEntity("A1", 200)));
        when(bankAccountRepository.findByNumber("A2")).thenReturn(Optional.of(anAccountEntity("A2", 50)));

        // Act
        FundTransferResponse response = transactionService.fundTransfer(aTransfer("A1", "A2", 100));

        // Assert
        ArgumentCaptor<TransactionEntity> saved = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
            .extracting(TransactionEntity::getAmount)
            .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .containsExactly(BigDecimal.valueOf(-100), BigDecimal.valueOf(100));   // debit row is negated
        assertThat(response.getTransactionId()).matches(UUID_REGEX);
    }
}
```

The snippet mirrors the real `TransactionService.fundTransfer` (it reads through
`AccountService` *and* the repository, saves a negated debit row, and generates
the id with `UUID.randomUUID()`). When a test needs the **real** sibling service
(§3.1), drop `@InjectMocks` and call the constructor explicitly.

`@ExtendWith(MockitoExtension.class)` runs in strict-stubs mode: unused stubs fail
the test. That is intended — it keeps arrange blocks honest. `mock(X.class)` in
`@BeforeEach` (the current style) remains acceptable when a test needs lenient
stubbing shared across many cases.

### 3.1 Mocking rules

Mock **ports**, not **things**:

| Mock it | Never mock it |
|---|---|
| Spring Data repositories (`*Repository`) | Entities, DTOs, requests/responses, enums |
| Feign clients (`BankingCoreFeignClient`, `BankingCoreRestClient`) | Mappers (`*Mapper`, `BaseMapper`) — use the real one |
| `KeycloakManager` / `KeycloakUserService` (Keycloak admin client boundary) | Other `@Service` classes in the same module when a real instance is cheap (prefer the real `AccountService` with mocked repositories over mocking `AccountService` — mock a sibling service only when its own dependencies are heavy) |
| `Clock` / ID generators (inject them; never `mockStatic` `LocalDateTime`/`UUID`) | Static utilities, `BeanUtils`, Lombok-generated code |

Conventions:

- Use `when(...).thenReturn(...)` (not BDDMockito) — matches the existing suites.
- Stub only what the test path needs; use `any()` matchers sparingly and never
  for the value that is the point of the test.
- Assert **state through outputs and captured arguments** (`ArgumentCaptor`),
  not through `verify` call counts alone. `verifyNoInteractions`/`never()` are
  for negative cases ("nothing was saved when funds were insufficient").
- Exceptions: `assertThatThrownBy(() -> ...).isInstanceOf(InsufficientFundsException.class)`
  and, where relevant, `.extracting("code").isEqualTo(GlobalErrorCode.INSUFFICIENT_FUNDS)`
  (`SimpleBankingGlobalException` exposes `code`, not `errorCode`).
- Non-determinism: `TransactionService` uses `UUID.randomUUID()` and auditing
  fills timestamps via `AuditingEntityListener`. Assert ids with
  `matches(UUID_REGEX)` / `isNotBlank()` and timestamps with `isCloseTo(now, within(...))`;
  when exact values matter, introduce a `Supplier<String>` / `Clock` constructor
  seam and stub it.
- `@MockBean` is allowed **only** inside `@WebMvcTest` slices (§4.1). It is
  banned in unit tests (needs a context), unnecessary in `@DataJpaTest`, and
  discouraged in `@SpringBootTest` (breaks context caching); replace external
  HTTP with WireMock instead.

### 3.2 Test data

- Put fixtures in `src/test/java/com/javatodev/finance/fixture/` (e.g.
  `AccountFixtures.anAccount(String number, long balance)`,
  `TransferFixtures.aTransfer(...)`). Fixtures return fully valid objects with
  overridable fields; tests only set what matters for the scenario.
- Use `BigDecimal.valueOf(...)` and `assertThat(x).isEqualByComparingTo(...)` for
  money — never `equals` on `BigDecimal`.
- No shared mutable state between tests; no `static` fixtures that get mutated.
- **Shared cross-service fixture contract.** The only seeded data in the system is
  core's `V1.0.20210427174721__temp_data.sql` (users, bank accounts
  `100015003000` / `100015003001`, utility accounts); the other services start
  empty and reference core ids. Those numbers are the single vocabulary used by
  unit fixtures, the WireMock `core-banking-service` mappings (§5.3) and the e2e
  suite — define them once per module in `CoreBankingFixtures` and never invent
  new account numbers in individual tests.
- Mappers (`BaseMapper` subclasses using `BeanUtils.copyProperties`) are plain
  classes — `new BankAccountMapper()` in a unit test, no Spring, no mocking; they
  deserve a round-trip test because `copyProperties` silently skips renamed fields.

## 4. Slice tests

### 4.1 Controllers — `@WebMvcTest` (part of `./gradlew test`)

One test class per controller, `<Controller>Test`, with `@MockBean` for the
service(s) it calls. Assert **HTTP contract only**: status, headers, JSON body,
validation errors, and the `GlobalExceptionHandler` mapping.

```java
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TransactionService transactionService;

    @Test
    void fundTransfer_insufficientFunds_returns400WithErrorCode() throws Exception {
        when(transactionService.fundTransfer(any())).thenThrow(new InsufficientFundsException("...", GlobalErrorCode.INSUFFICIENT_FUNDS));

        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aTransfer("A1", "A2", 100))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INSUFFICIENT_FUNDS));
    }
}
```

Services with `AppAuthUserFilter` (user, fund-transfer, utility-payment): the
filter is registered by `AuditConfig` via a `FilterRegistrationBean`, and
`@WebMvcTest` does not scan that class — nor should it be `@Import`ed, because
`@EnableJpaAuditing` on it needs an `EntityManagerFactory`. Register the filter
in the slice with a `@TestConfiguration` declaring the same
`FilterRegistrationBean<AppAuthUserFilter>` (or `MockMvcBuilders…addFilters(new AppAuthUserFilter())`).
The auth id is *not* an argument to the service — it lives in the
`ApiRequestContextHolder` `ThreadLocal` and is cleared in the filter's `finally` —
so capture it inside the call:
`doAnswer(inv -> { seenAuthId = ApiRequestContextHolder.getContext().getAuthId(); return ...; })`
on the `@MockBean` service, then assert it equals the `X-Auth-Id` header sent.

## 5. Integration tests (`./gradlew integrationTest`)

**Definition:** the Spring context of **one** module against real MySQL in a
container (Flyway where the module has migrations), real HTTP in/out, with every
*other* service replaced by WireMock (Keycloak excepted — see the user-service
row). No Eureka, no Config Server, no Zipkin. Two shapes, both `@Tag("integration")`:

### 5.1 Repositories — `@DataJpaTest`

Only for repositories with **custom queries** (derived `findBy...`, `@Query`,
ordering, pagination, date ranges). Plain `JpaRepository` CRUD is not tested.

- Class `<Repository>IT`, `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)`,
  `@Import(MySqlTestcontainerConfig.class)` (§5.2). A slice cannot share the
  `@SpringBootTest` context (different configuration → different cache key), so a
  module with both shapes runs **two** contexts; the container is still one
  because `MySqlTestcontainerConfig` hands out a JVM singleton (§5.2). This is
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

### 5.2 Full module — `@SpringBootTest`

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
must stay Docker-free like `test`. CI runs `integrationTest` explicitly (§7).
Because `jacocoTestReport` only reads `test.exec`, integration coverage is
excluded from the report unless the module adds
`jacocoTestReport { executionData fileTree(layout.buildDirectory).include("jacoco/*.exec") }`
and `integrationTest { finalizedBy jacocoTestReport }` — do that only once the
threshold in §7 is enabled.

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
normally receives from the external config repo (see §1). Persistence differs
because **only `core-banking-service` has Flyway and migrations**; user,
fund-transfer and utility-payment have no `flyway` dependency, no `db/migration`,
and get their schema from `ddl-auto: update` in the external config:

| Module | `spring.flyway.enabled` | `spring.jpa.hibernate.ddl-auto` | Migration fidelity |
|---|---|---|---|
| `core-banking-service` | `true` | `validate` | real — migrations + entity/DDL drift both caught |
| `user`, `fund-transfer`, `utility-payment` | `false` | `create-drop` | none today; adopting Flyway in these three modules is a prerequisite (§9) |

Per-module responsibilities (assertions describe the **current** code; intended
changes are marked):

| Module | Integration tests prove |
|---|---|
| `core-banking-service` | Migrations apply on MySQL and `validate` passes against the entities; `POST /api/v1/transaction/fund-transfer` debits/credits both `banking_core_account` rows and writes two `banking_core_transaction` rows (negated debit) in one transaction; `InsufficientFunds` leaves balances unchanged. |
| `fund-transfer-service`, `utility-payment-service` | `PENDING` (`PROCESSING` for payments) row is saved, core is called once with the right body (`WireMock.verify`), row moves to `SUCCESS` with the returned reference. **Failure path (current code):** on a core 4xx/5xx the `FeignException` propagates through `GlobalExceptionHandler#handleException` as HTTP `400` with a string body and the row **stays `PENDING`/`PROCESSING`** — there is no `FAILED` transition and no `CustomFeignErrorDecoder` in these two modules. Neither service method is `@Transactional`, so the `PENDING`/`PROCESSING` row is committed by the first `save` before the remote call and survives the failure; pin that the row exists, is not `SUCCESS`, and core was called exactly once. A `FAILED` status is a product change to implement first, then assert. |
| `user-service` | Real Keycloak via `dasniko/testcontainers-keycloak` importing `docker-compose/keycloak/realm-export.json` (it already contains the `javatodev-internet-banking-kc-api-client` service account the code uses); core-banking via WireMock. User is created in Keycloak and locally with `authId`; duplicate registration maps to `UserAlreadyRegisteredException`; core `400` is mapped by `CustomFeignErrorDecoder` into an `ErrorResponse`. WireMock is *not* used for Keycloak: `KeycloakProperties` builds a **static** admin client (`client_credentials` token endpoint + stateful search/create/get/update calls), which is impractical to stub and would pin the first port for the JVM. |
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

### 5.3 WireMock stubs

Stub files live under `src/test/resources/wiremock/<downstream-service>/` using
WireMock's root layout — `mappings/*.json` for stubs and `__files/` for response
bodies — and each `WireMockServer` is created with
`usingFilesUnderClasspath("wiremock/<downstream-service>")` so the mappings are
loaded at startup (one server per downstream). The same core-banking mapping
files are copied verbatim across fund-transfer, utility-payment and user
services and use the shared fixture account numbers (§3.2). Inline `stubFor(...)`
is fine for per-test error cases; `resetAll()` restores the file mappings.

### 5.4 Docker requirement

`integrationTest` requires a Docker daemon (Testcontainers). `./gradlew test` and
`./gradlew build` never do. Developers without Docker still get unit + slice +
smoke coverage locally; CI runs both tasks. `testcontainers.reuse.enable=true`
(`~/.testcontainers.properties`) is fine locally, never in CI.

### 5.5 Contract tests (future)

Fund-transfer, utility-payment and user services each carry their own copy of
core-banking DTOs (`AccountResponse`, `FundTransferResponse`). The WireMock stub
files above are the *de facto* contract. If the provider contract keeps drifting,
graduate those stubs to Spring Cloud Contract (provider-side verification in
`core-banking-service`, published stubs consumed by the others). Not adopted now.

## 6. End-to-end tests

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

## 7. CI

`.github/workflows/ci.yml` (per-module matrix, already exists) is extended, not
replaced:

| Job | Trigger | Command |
|---|---|---|
| `test` (matrix) | every push/PR | `./gradlew test` — unit + slice + smoke, JaCoCo report (current). |
| `integration-test` (matrix) — *to be added* | every push/PR, after `test` | `./gradlew integrationTest` — ubuntu-latest has Docker for Testcontainers. |
| `e2e` — *to be added* | nightly `schedule` + `workflow_dispatch` + release tags | `bootJar` in all seven modules → compose up with the e2e override (built from the checkout) → `e2e-tests` → compose down (`if: always()`); upload REST Assured logs on failure. |

Until the two new jobs exist, only `./gradlew test` is enforced; the integration
and e2e layers are the target state described in §9.

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

## 8. Checklist for a new or changed feature

1. Unit tests for every new/changed `service/` method: happy path, each error
   path (`assertThatThrownBy`), boundaries (inclusive ranges, zero/negative
   amounts, empty results).
2. `@WebMvcTest` case for every new endpoint or changed status/error mapping.
3. `@DataJpaTest` (MySQL Testcontainers, `@Tag("integration")`) for every new repository query.
4. One `IT` per new cross-service interaction or migration; in core, a new
   migration must keep `ddl-auto: validate` green.
5. Extend the relevant `E2E` flow only if the change is visible through the gateway.
6. `./gradlew test` and `./gradlew integrationTest` green in the module.

## 9. Adoption order

This document is the target state. Recommended order to get there:

1. Add `integrationTest` task + `src/test/resources/bootstrap.yml` +
   `application-integration.yml` + Testcontainers / WireMock deps to
   `core-banking-service`; write `MySqlTestcontainerConfig`,
   `AbstractIntegrationTest`, `FundTransferIT`; add the `integration-test` CI job.
2. Convert `AccountServiceTest`/`TransactionServiceTest` assertions to AssertJ
   and add `@WebMvcTest` classes for the three core controllers.
3. Repeat step 1 for fund-transfer, utility-payment and user services (WireMock
   stubs for core-banking shared under `src/test/resources/wiremock/`; user-service
   adds the Keycloak Testcontainer). These three run `ddl-auto: create-drop` until
   they adopt Flyway — do that adoption before relying on them for migration fidelity.
4. Gateway `WebTestClient` + `mockJwt()` routing tests with test-local routes.
5. `e2e-tests` module + `docker-compose.e2e.yml` build override + nightly workflow.
6. Enable JaCoCo thresholds (and merge `integrationTest` coverage, §5.2).
