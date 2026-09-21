# Unit tests

Part of the [testing strategy](../../TESTING.md).

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
([Mocking rules](#mocking-rules)), drop `@InjectMocks` and call the constructor explicitly.

`@ExtendWith(MockitoExtension.class)` runs in strict-stubs mode: unused stubs fail
the test. That is intended — it keeps arrange blocks honest. `mock(X.class)` in
`@BeforeEach` (the current style) remains acceptable when a test needs lenient
stubbing shared across many cases.

## Mocking rules

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
- `@MockBean` is allowed **only** inside `@WebMvcTest` slices ([slice tests](slice-tests.md)). It is
  banned in unit tests (needs a context), unnecessary in `@DataJpaTest`, and
  discouraged in `@SpringBootTest` (breaks context caching); replace external
  HTTP with WireMock instead.

## Test data

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
  unit fixtures, the WireMock `core-banking-service` mappings ([WireMock stubs](integration-tests.md#wiremock-stubs)) and the e2e
  suite — define them once per module in `CoreBankingFixtures` and never invent
  new account numbers in individual tests.
- Mappers (`BaseMapper` subclasses using `BeanUtils.copyProperties`) are plain
  classes — `new BankAccountMapper()` in a unit test, no Spring, no mocking; they
  deserve a round-trip test because `copyProperties` silently skips renamed fields.
