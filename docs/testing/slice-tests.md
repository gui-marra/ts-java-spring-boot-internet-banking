# Slice tests

Part of the [testing strategy](../../TESTING.md).

## Controllers — `@WebMvcTest` (part of `./gradlew test`)

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
