package com.javatodev.finance;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.entity.FundTransferEntity;
import com.javatodev.finance.model.repository.FundTransferRepository;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.javatodev.finance.fixture.FundTransferFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.FundTransferFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.FundTransferFixtures.AUTH_ID;
import static com.javatodev.finance.fixture.FundTransferFixtures.TRANSACTION_ID;
import static com.javatodev.finance.fixture.FundTransferFixtures.TRANSACTION_ID_2;
import static com.javatodev.finance.fixture.FundTransferFixtures.aFundTransferRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FundTransferIT extends AbstractIntegrationTest {

    private static final String TRANSFER_URL = "/api/v1/transfer";
    private static final String CORE_TRANSFER_PATH = "/api/v1/transaction/fund-transfer";
    private static final String AUTH_HEADER = "X-Auth-Id";
    private static final String MIGRATION_VERSION = "1.0.20260921054802";
    private static final String GENERIC_ERROR_PREFIX = "Exception occur inside API";

    @Autowired
    private FundTransferRepository fundTransferRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        fundTransferRepository.deleteAll();
    }

    @Test
    void contextStarts_flywayBaselineApplied_schemaValidates() {
        // Assert
        assertThat(fundTransferRepository.count()).isZero();
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
            "SELECT version, success FROM flyway_schema_history");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("version")).isEqualTo(MIGRATION_VERSION);
        assertThat(history.get(0).get("success")).isIn(true, 1, (byte) 1);
    }

    @Test
    void fundTransfer_happyPath_returnsTransactionIdAndPersistsSuccessRow() throws Exception {
        // Act
        postTransfer(100)
            // Assert
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId").value(TRANSACTION_ID))
            .andExpect(jsonPath("$.message").value("Fund Transfer Successfully Completed"));

        List<FundTransferEntity> rows = fundTransferRepository.findAll();
        assertThat(rows).hasSize(1);
        FundTransferEntity row = rows.get(0);
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(row.getTransactionReference()).isEqualTo(TRANSACTION_ID);
        assertThat(row.getFromAccount()).isEqualTo(ACCOUNT_NUMBER_1);
        assertThat(row.getToAccount()).isEqualTo(ACCOUNT_NUMBER_2);
        assertThat(row.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(row.getCreatedBy()).isEqualTo(AUTH_ID);
        assertThat(row.getModifiedBy()).isEqualTo(AUTH_ID);
        assertThat(row.getCreatedDate()).isNotNull();
        assertThat(row.getModifiedDate()).isNotNull();
        assertThat(row.getVersion()).isEqualTo(1L);

        CoreBankingStub.SERVER.verify(1, postRequestedFor(urlPathEqualTo(CORE_TRANSFER_PATH))
            .withRequestBody(matchingJsonPath("$.fromAccount", equalTo(ACCOUNT_NUMBER_1)))
            .withRequestBody(matchingJsonPath("$.toAccount", equalTo(ACCOUNT_NUMBER_2)))
            .withRequestBody(matchingJsonPath("$.amount", equalTo("100"))));
    }

    @Test
    void fundTransfer_happyPath_doesNotForwardAuthHeaderButForwardsAuthIdInBody() throws Exception {
        // Act
        postTransfer(100).andExpect(status().isOk());

        // Assert
        List<LoggedRequest> coreRequests = CoreBankingStub.SERVER.findAll(postRequestedFor(urlPathEqualTo(CORE_TRANSFER_PATH)));
        assertThat(coreRequests).hasSize(1);
        assertThat(coreRequests.get(0).getHeader(AUTH_HEADER)).isNull();
        assertThat(coreRequests.get(0).getBodyAsString()).contains("\"authID\":\"" + AUTH_ID + "\"");
    }

    @Test
    void fundTransfer_coreReturns400_returns400AndRowStaysPendingWithoutFailedState() throws Exception {
        // Arrange
        stubCoreFailure(aResponse().withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"code\":\"INSUFFICIENT_FUNDS\",\"message\":\"Insufficient funds\"}"));

        // Act
        postTransfer(100)
            // Assert
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith(GENERIC_ERROR_PREFIX)));

        assertSinglePendingRow();
        CoreBankingStub.SERVER.verify(1, postRequestedFor(urlPathEqualTo(CORE_TRANSFER_PATH)));
    }

    @Test
    void fundTransfer_coreReturns500_returns400AndRowStaysPendingWithoutFailedState() throws Exception {
        // Arrange
        stubCoreFailure(aResponse().withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"code\":\"INTERNAL_ERROR\",\"message\":\"boom\"}"));

        // Act
        postTransfer(100)
            // Assert
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith(GENERIC_ERROR_PREFIX)));

        assertSinglePendingRow();
        CoreBankingStub.SERVER.verify(1, postRequestedFor(urlPathEqualTo(CORE_TRANSFER_PATH)));
    }

    @Test
    void fundTransfer_coreConnectionReset_returns400AndRowStaysPendingWithoutFailedState() throws Exception {
        // Arrange
        stubCoreFailure(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        // Act
        postTransfer(100)
            // Assert
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith(GENERIC_ERROR_PREFIX)));

        assertSinglePendingRow();
        CoreBankingStub.SERVER.verify(1, postRequestedFor(urlPathEqualTo(CORE_TRANSFER_PATH)));
    }

    @Test
    @Disabled("FundTransferService marks SUCCESS even when core returns no transactionId — see issue #TBD")
    void fundTransfer_coreReturns200WithoutTransactionId_rowIsNotMarkedSuccess() throws Exception {
        // Arrange
        stubCoreFailure(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"message\":\"ok\"}"));

        // Act
        postTransfer(100).andExpect(status().isBadRequest());

        // Assert
        List<FundTransferEntity> rows = fundTransferRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isNotEqualTo(TransactionStatus.SUCCESS);
        assertThat(rows.get(0).getTransactionReference()).isNull();
    }

    @Test
    void readFundTransfers_afterTwoSuccessfulTransfers_returnsBothRows() throws Exception {
        // Arrange
        postTransfer(100).andExpect(status().isOk());
        postTransfer(50).andExpect(status().isOk());

        // Act & Assert
        mockMvc.perform(get(TRANSFER_URL).header(AUTH_HEADER, AUTH_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].transactionReference").value(TRANSACTION_ID))
            .andExpect(jsonPath("$[1].transactionReference").value(TRANSACTION_ID_2))
            .andExpect(jsonPath("$[0].fromAccount").value(ACCOUNT_NUMBER_1))
            .andExpect(jsonPath("$[0].toAccount").value(ACCOUNT_NUMBER_2))
            .andExpect(jsonPath("$[0].amount").value(100.0))
            .andExpect(jsonPath("$[1].amount").value(50.0));
    }

    @Test
    @Disabled("FundTransfer.status is a String while the entity uses the TransactionStatus enum, so "
        + "BeanUtils.copyProperties skips it and GET returns status=null — see issue #TBD")
    void readFundTransfers_afterSuccessfulTransfer_exposesStatusSuccess() throws Exception {
        // Arrange
        postTransfer(100).andExpect(status().isOk());

        // Act & Assert
        mockMvc.perform(get(TRANSFER_URL).header(AUTH_HEADER, AUTH_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void readFundTransfers_pageSizeOne_returnsSingleElement() throws Exception {
        // Arrange
        postTransfer(100).andExpect(status().isOk());
        postTransfer(50).andExpect(status().isOk());

        // Act & Assert
        mockMvc.perform(get(TRANSFER_URL).param("page", "0").param("size", "1").header(AUTH_HEADER, AUTH_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].transactionReference").value(TRANSACTION_ID));
    }

    @Test
    void readFundTransfers_emptyTable_returnsEmptyArray() throws Exception {
        // Act & Assert
        mockMvc.perform(get(TRANSFER_URL).header(AUTH_HEADER, AUTH_ID))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void fundTransfer_twoSequentialTransfers_createsTwoRowsWithIncreasingIdsAndCallsCoreTwice() throws Exception {
        // Act
        postTransfer(100).andExpect(status().isOk()).andExpect(jsonPath("$.transactionId").value(TRANSACTION_ID));
        postTransfer(50).andExpect(status().isOk()).andExpect(jsonPath("$.transactionId").value(TRANSACTION_ID_2));

        // Assert
        List<FundTransferEntity> rows = fundTransferRepository.findAll();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(FundTransferEntity::getStatus).containsOnly(TransactionStatus.SUCCESS);
        assertThat(rows).extracting(FundTransferEntity::getTransactionReference)
            .containsExactly(TRANSACTION_ID, TRANSACTION_ID_2);
        assertThat(rows.get(1).getId()).isGreaterThan(rows.get(0).getId());
        CoreBankingStub.SERVER.verify(2, postRequestedFor(urlPathEqualTo(CORE_TRANSFER_PATH)));
    }

    private ResultActions postTransfer(long amount) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(TRANSFER_URL)
            .header(AUTH_HEADER, AUTH_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, amount))));
    }

    private void stubCoreFailure(ResponseDefinitionBuilder response) {
        CoreBankingStub.SERVER.stubFor(post(urlPathEqualTo(CORE_TRANSFER_PATH)).atPriority(1).willReturn(response));
    }

    private void assertSinglePendingRow() {
        List<FundTransferEntity> rows = fundTransferRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(rows.get(0).getTransactionReference()).isNull();
        assertThat(rows.get(0).getVersion()).isZero();
    }
}
