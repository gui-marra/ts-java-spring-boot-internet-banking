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
| **Slice** | Controller contract (`@WebMvcTest`), JPA queries (`@DataJpaTest`) | One Spring layer, rest mocked | Every PR (`./gradlew test`) | < 1 s each |
| **Integration** | One service wired end-to-end against real infra (MySQL, downstream HTTP) | Full `@SpringBootTest` for **one** module | Every PR (`./gradlew integrationTest`) | seconds |
| **End-to-end** | Business flows through the gateway across services | Whole Docker Compose stack | Nightly / manual / pre-release | minutes |

Rule of thumb: push a test as far *down* the pyramid as it can go and still prove
the behaviour. Most tests are unit tests; integration tests cover wiring
(migrations, JSON, filters, Feign); e2e tests cover only the money-moving flows.

Coverage is measured with JaCoCo (already configured in every module). The
`service/` package of each module is the coverage target; controllers, DTOs,
entities, mappers and configuration are exercised through slice/integration tests
and are not chased for coverage numbers.

## 2. Frameworks

All of these are provided by `spring-boot-starter-test` unless noted. Versions are
managed by the Spring Boot BOM (`3.2.x`) — do not pin them individually.

| Concern | Library | Notes |
|---|---|---|
| Test runner | **JUnit 5 (Jupiter)** | `useJUnitPlatform()` is already enabled. No JUnit 4, no Vintage. |
| Assertions | **AssertJ** | `assertThat(...)` fluent API for all new tests. JUnit `Assertions.*` are allowed only in existing tests until touched. |
| Mocking | **Mockito** (`mockito-core`, `mockito-junit-jupiter`) | See §3. No PowerMock, no static mocking of production code. |
| Spring test support | `spring-boot-test`, `spring-test` | `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`, `MockMvc`, `WebTestClient`. |
| Security in tests | `spring-security-test` | Gateway only (`mockJwt()` / `SecurityMockServerConfigurers`). |
| JSON assertions | JsonPath (bundled) + `JSONassert` | `jsonPath("$.x")` in MockMvc; `JSONAssert.assertEquals` for full-body contracts. |
| Real databases | **Testcontainers** (`org.testcontainers:mysql`, `junit-jupiter`) + `spring-boot-testcontainers` | Integration/slice tests that must run against MySQL (Flyway + dialect fidelity). |
| HTTP stubbing | **WireMock** (`org.wiremock:wiremock-standalone`) | Stubs `core-banking-service` for Feign-based services and downstream routes for the gateway. |
| HTTP client for e2e | **REST Assured** (`io.rest-assured:rest-assured`) | Only in the `e2e-tests` module. |
| Test data | Lombok `@Builder` on entities + hand-written `*Fixtures` classes | No Instancio/Podam/EasyRandom — deterministic data only. |

Deliberately **not** used: Spock/Groovy (single language keeps CI and IDE setup
trivial), Cucumber (no non-developer stakeholders write scenarios), Pact/Spring
Cloud Contract (see §5.4 — revisit if consumers of `core-banking-service` grow).

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
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(anAccount("A1", 200)));
        when(bankAccountRepository.findByNumber("A2")).thenReturn(Optional.of(anAccount("A2", 50)));
        ...
        // Act
        FundTransferResponse response = transactionService.fundTransfer(aTransfer("A1", "A2", 100));

        // Assert
        ArgumentCaptor<TransactionEntity> saved = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
            .extracting(TransactionEntity::getTransactionType, TransactionEntity::getAmount)
            .containsExactly(tuple(TransactionType.FUND_TRANSFER, BigDecimal.valueOf(100)), ...);
        assertThat(response.getTransactionId()).isNotBlank();
    }
}
```

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
  and, where relevant, `.extracting("errorCode").isEqualTo(GlobalErrorCode.INSUFFICIENT_FUNDS)`.
- `@MockBean` is allowed **only** inside slice tests (§4). It is banned in unit
  tests (needs a context) and discouraged in `@SpringBootTest` (breaks context
  caching); replace external HTTP with WireMock instead.

### 3.2 Test data

- Put fixtures in `src/test/java/com/javatodev/finance/fixture/` (e.g.
  `AccountFixtures.anAccount(String number, long balance)`,
  `TransferFixtures.aTransfer(...)`). Fixtures return fully valid objects with
  overridable fields; tests only set what matters for the scenario.
- Use `BigDecimal.valueOf(...)` and `assertThat(x).isEqualByComparingTo(...)` for
  money — never `equals` on `BigDecimal`.
- No shared mutable state between tests; no `static` fixtures that get mutated.

## 4. Slice tests (part of `./gradlew test`)

### 4.1 Controllers — `@WebMvcTest`

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

Services that use `AppAuthUserFilter` register the filter in the slice and send
`X-Auth-Id` in requests; assert the `ApiRequestContextHolder` value reaches the
service via `ArgumentCaptor`.

### 4.2 Repositories — `@DataJpaTest`

Only for repositories with **custom queries** (derived `findBy...`, `@Query`,
ordering, pagination, date ranges). Plain `JpaRepository` CRUD is not tested.

- Run against **MySQL via Testcontainers** with Flyway enabled
  (`@Testcontainers`, `@ServiceConnection MySQLContainer`,
  `@AutoConfigureTestDatabase(replace = NONE)`). This is what catches dialect and
  migration drift; H2 hides both.
- H2 (`spring.flyway.enabled=false`, `ddl-auto: none`, the current
  `src/test/resources/application.yml`) is kept **only** as the fallback used by
  the `contextLoads` smoke tests so `./gradlew test` never requires Docker.
- Seed data per test through `TestEntityManager`, not through shared SQL scripts,
  except for Flyway's own `temp_data` migration which tests may rely on for
  read-only cases.

## 5. Integration tests (`./gradlew integrationTest`)

**Definition:** the whole Spring context of **one** module, real MySQL in a
container, real Flyway migrations, real HTTP in/out — with every *other* service
replaced by WireMock. No Eureka, no Config Server, no Keycloak, no Zipkin.

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
    useJUnitPlatform { includeTags 'integration' }
    shouldRunAfter test
}
check.dependsOn integrationTest
```

**Shared base class** per module, `AbstractIntegrationTest`:

```java
@Tag("integration")
@Testcontainers
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Container @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");   // one per JVM, reused across classes

    @RegisterExtension
    static final WireMockExtension CORE_BANKING = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void wiremockUrls(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.core-banking-service.url", CORE_BANKING::baseUrl);
    }
}
```

`src/test/resources/application-integration.yml` disables the platform pieces:
`eureka.client.enabled=false`, `spring.cloud.config.enabled=false`,
`spring.cloud.discovery.enabled=false`, `management.tracing.enabled=false`,
`spring.flyway.enabled=true`. Feign clients need a direct `url` for the stubbed
service (`@FeignClient(value = "core-banking-service", url = "${clients.core-banking.url:}")`
— empty falls back to discovery in production).

Per-module responsibilities:

| Module | Integration tests prove |
|---|---|
| `core-banking-service` | Migrations apply on MySQL; `POST /api/v1/transaction/fund-transfer` debits/credits both `banking_core_*` rows atomically; `InsufficientFunds` leaves balances unchanged (transactional rollback). |
| `fund-transfer-service`, `utility-payment-service` | `PENDING` row is saved, core is called with the right body (`WireMock.verify`), row moves to `SUCCESS` with the returned reference; core 4xx/5xx leaves a `PENDING`/`FAILED` row and surfaces a mapped error via `GlobalExceptionHandler` + `CustomFeignErrorDecoder`. |
| `user-service` | Keycloak admin client is stubbed with WireMock (`/admin/realms/.../users`); user is created locally with `authId`; duplicate registration maps to `UserAlreadyRegisteredException`. |
| `api-gateway` | `WebTestClient` + `mockJwt()`: routes `/banking-core/**`, `/fund-transfer/**`, `/utility-payment/**`, `/user/**` are forwarded to WireMock downstreams with `X-Auth-Id` populated from the JWT subject; requests without a token get `401`. |
| `config-server`, `service-registry` | `contextLoads` only. |

### 5.1 Context caching

Keep the integration configuration identical across test classes (same base
class, same profile, no per-class `@MockBean`/`@TestPropertySource`) so Spring
starts **one** context per module. Test-level state isolation is done with
`@Sql(scripts = "/sql/clean.sql", executionPhase = BEFORE_TEST_METHOD)` or
`@Transactional` on the test class (only when the test does not exercise
transactional boundaries itself).

### 5.2 WireMock stubs

Stub files live in `src/test/resources/wiremock/<downstream-service>/*.json`
(WireMock's `mappings` format) so the same core-banking responses are reused
across fund-transfer, utility-payment and user services. Inline `stubFor(...)`
is fine for per-test error cases.

### 5.3 Docker requirement

`integrationTest` requires a Docker daemon (Testcontainers). `./gradlew test`
never does. Developers without Docker still get unit + slice + smoke coverage
locally; CI runs both tasks.

### 5.4 Contract tests (future)

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

1. `cd docker-compose && docker compose up -d --build` — wait until Eureka shows
   five client registrations (see `.agents/skills/banking-stack-testing/SKILL.md`).
2. `cd e2e-tests && ./gradlew test -Dgateway.url=http://localhost:8082 -Dkeycloak.url=http://localhost:8080`
3. `docker compose down`.

The test module never starts containers itself (a `ComposeContainer` would make
the suite own a ~2 minute startup and hide image-vs-source ambiguity); the stack
is a precondition and its URLs are system properties with local defaults.

**Conventions:**

- Class suffix `E2E`, `@Tag("e2e")`; one class per business flow:
  `AuthenticationE2E`, `FundTransferE2E`, `UtilityPaymentE2E`, `UserLifecycleE2E`.
- Token via password grant with realm `javatodev-internet-banking`, the client
  and secret read from `docker-compose/keycloak/realm-export.json`, credentials
  from `E2E_USERNAME` / `E2E_PASSWORD` env vars (defaults are the seeded
  `ib_admin@javatodev.com` account). Never commit tokens or log them.
- **Read before mutate:** every money test reads the source and destination
  balances first and asserts `before - amount == after` — never a hard-coded
  expected balance, because the compose volume persists between runs.
- Assert both the response (`200`, `transactionId`) **and** the effect (account
  balances via `/banking-core/api/v1/account/bank-account/{number}`, transfer
  record via `/fund-transfer/api/v1/transfer` filtered by `transactionReference`).
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
| `integration-test` (matrix) | every push/PR, after `test` | `./gradlew integrationTest` — ubuntu-latest has Docker for Testcontainers. |
| `e2e` | nightly `schedule` + `workflow_dispatch` + release tags | compose up → `e2e-tests` → compose down; upload REST Assured logs on failure. |

Coverage thresholds (`jacocoTestCoverageVerification`) are **not** enforced yet.
Once the `service/` packages are covered by tests following this document, add a
`minimum = 0.80` line-coverage rule scoped to `com.javatodev.finance.service.*`
per module and let CI fail on regressions.

## 8. Checklist for a new or changed feature

1. Unit tests for every new/changed `service/` method: happy path, each error
   path (`assertThatThrownBy`), boundaries (inclusive ranges, zero/negative
   amounts, empty results).
2. `@WebMvcTest` case for every new endpoint or changed status/error mapping.
3. `@DataJpaTest` (MySQL Testcontainers) for every new repository query.
4. One `IT` per new cross-service interaction or migration.
5. Extend the relevant `E2E` flow only if the change is visible through the gateway.
6. `./gradlew test` and `./gradlew integrationTest` green in the module.

## 9. Adoption order

This document is the target state. Recommended order to get there:

1. Add `integrationTest` task + `application-integration.yml` + Testcontainers
   / WireMock deps to `core-banking-service`; write `FundTransferIT`.
2. Convert `AccountServiceTest`/`TransactionServiceTest` assertions to AssertJ
   and add `@WebMvcTest` classes for the three core controllers.
3. Repeat step 1 for fund-transfer, utility-payment and user services (WireMock
   stubs for core-banking shared under `src/test/resources/wiremock/`).
4. Gateway `WebTestClient` + `mockJwt()` routing tests.
5. `e2e-tests` module + nightly workflow.
6. Enable JaCoCo thresholds.
