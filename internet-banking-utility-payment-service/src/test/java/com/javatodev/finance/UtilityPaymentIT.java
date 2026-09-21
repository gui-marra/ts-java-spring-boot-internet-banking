package com.javatodev.finance;

import com.javatodev.finance.fixture.CoreBankingFixtures;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import com.javatodev.finance.repository.UtilityPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.CORE_TRANSACTION_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.REFERENCE_NUMBER;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_UNKNOWN_ID;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UtilityPaymentIT extends AbstractIntegrationTest {

    private static final String PAYMENT_URL = "/api/v1/utility-payment";
    private static final String CORE_PAYMENT_URL = "/api/v1/transaction/util-payment";

    @Autowired
    private UtilityPaymentRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void success() throws Exception {
        String requestJson = requestJson(CoreBankingFixtures.aUtilityPaymentRequest());

        mockMvc.perform(post(PAYMENT_URL)
                .contentType(APPLICATION_JSON)
                .header("X-Auth-Id", "it-user")
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId").value(CORE_TRANSACTION_ID))
            .andExpect(jsonPath("$.message").exists());

        UtilityPaymentEntity row = onlyRow();
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(row.getTransactionId()).isEqualTo(CORE_TRANSACTION_ID);
        assertThat(row.getAmount()).isEqualByComparingTo("250");
        assertThat(row.getCreatedBy()).isEqualTo("it-user");
        assertThat(row.getCreatedDate()).isNotNull();
        CoreBankingWireMock.SERVER.verify(1, postRequestedFor(urlEqualTo(CORE_PAYMENT_URL))
            .withRequestBody(equalToJson(requestJson, true, true)));
    }

    @Test
    void core400_unknownProvider() throws Exception {
        String requestJson = requestJson(CoreBankingFixtures.aUtilityPaymentRequest(
            ACCOUNT_NUMBER_1, UTILITY_PROVIDER_UNKNOWN_ID, 250));

        MvcResult result = mockMvc.perform(post(PAYMENT_URL)
                .contentType(APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertExceptionBody(result.getResponse().getContentAsString());
        UtilityPaymentEntity row = onlyRow();
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(row.getTransactionId()).isNull();
        CoreBankingWireMock.SERVER.verify(1, postRequestedFor(urlEqualTo(CORE_PAYMENT_URL))
            .withRequestBody(equalToJson(requestJson, true, true)));
    }

    @Test
    void core500() throws Exception {
        CoreBankingWireMock.SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(CORE_PAYMENT_URL))
            .atPriority(1)
            .willReturn(aResponse().withStatus(500)));
        String requestJson = requestJson(CoreBankingFixtures.aUtilityPaymentRequest());

        MvcResult result = mockMvc.perform(post(PAYMENT_URL)
                .contentType(APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertExceptionBody(result.getResponse().getContentAsString());
        UtilityPaymentEntity row = onlyRow();
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(row.getTransactionId()).isNull();
        CoreBankingWireMock.SERVER.verify(1, postRequestedFor(urlEqualTo(CORE_PAYMENT_URL))
            .withRequestBody(equalToJson(requestJson, true, true)));
    }

    @Test
    void core200MalformedBody() throws Exception {
        CoreBankingWireMock.SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(CORE_PAYMENT_URL))
            .atPriority(1)
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("not json")));
        String requestJson = requestJson(CoreBankingFixtures.aUtilityPaymentRequest());

        MvcResult result = mockMvc.perform(post(PAYMENT_URL)
                .contentType(APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertExceptionBody(result.getResponse().getContentAsString());
        UtilityPaymentEntity row = onlyRow();
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(row.getTransactionId()).isNull();
        CoreBankingWireMock.SERVER.verify(1, postRequestedFor(urlEqualTo(CORE_PAYMENT_URL))
            .withRequestBody(equalToJson(requestJson, true, true)));
    }

    @Test
    void noAuthHeader() throws Exception {
        String requestJson = requestJson(CoreBankingFixtures.aUtilityPaymentRequest());

        mockMvc.perform(post(PAYMENT_URL)
                .contentType(APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk());

        assertThat(onlyRow().getCreatedBy()).isEqualTo("SYSTEM_USER");
    }

    @Test
    void readPayments() throws Exception {
        String requestJson = requestJson(CoreBankingFixtures.aUtilityPaymentRequest());
        mockMvc.perform(post(PAYMENT_URL)
                .contentType(APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk());

        mockMvc.perform(get(PAYMENT_URL)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$[0].amount").value(250))
            .andExpect(jsonPath("$[0].account").value(ACCOUNT_NUMBER_1))
            .andExpect(jsonPath("$[0].providerId").value(1))
            .andExpect(jsonPath("$[0].referenceNumber").value(REFERENCE_NUMBER))
            .andExpect(jsonPath("$[0].createdDate").doesNotExist())
            .andExpect(jsonPath("$[0].createdBy").doesNotExist());
    }

    @Test
    void flywayBaselineApplied() {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList(
            "select version, success from flyway_schema_history");

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.get("version")).isEqualTo("1.0.20260921055028");
            assertThat(row.get("success")).isIn(true, 1, "1");
        });
    }

    private String requestJson(Object request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private UtilityPaymentEntity onlyRow() {
        List<UtilityPaymentEntity> rows = rowsFor(REFERENCE_NUMBER);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private List<UtilityPaymentEntity> rowsFor(String ref) {
        return repo.findAll().stream()
            .filter(row -> ref.equals(row.getReferenceNumber()))
            .toList();
    }

    private void assertExceptionBody(String body) throws Exception {
        assertThat(body).startsWith("Exception occur inside API");
        assertThatThrownBy(() -> objectMapper.readTree(body))
            .isInstanceOf(Exception.class);
    }
}
