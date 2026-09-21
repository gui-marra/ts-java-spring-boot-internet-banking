package com.javatodev.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.service.TransactionService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aFundTransferRequest;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPaymentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private TransactionService transactionService;

    @Test
    void fundTransfer_validRequest_returns200WithTransactionId() throws Exception {
        // Arrange
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100);
        when(transactionService.fundTransfer(any())).thenReturn(FundTransferResponse.builder()
            .message("Transaction successfully completed")
            .transactionId("00000000-0000-0000-0000-000000000001")
            .build());

        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Transaction successfully completed"))
            .andExpect(jsonPath("$.transactionId").value("00000000-0000-0000-0000-000000000001"));

        ArgumentCaptor<FundTransferRequest> captor = ArgumentCaptor.forClass(FundTransferRequest.class);
        verify(transactionService).fundTransfer(captor.capture());
        assertThat(captor.getValue()).isEqualTo(request);
    }

    @Test
    void utilPayment_validRequest_returns200WithTransactionId() throws Exception {
        // Arrange
        UtilityPaymentRequest request =
            aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, 40);
        when(transactionService.utilPayment(any())).thenReturn(UtilityPaymentResponse.builder()
            .message("Utility payment successfully completed")
            .transactionId("00000000-0000-0000-0000-000000000002")
            .build());

        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/util-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Utility payment successfully completed"))
            .andExpect(jsonPath("$.transactionId").value("00000000-0000-0000-0000-000000000002"));

        ArgumentCaptor<UtilityPaymentRequest> captor = ArgumentCaptor.forClass(UtilityPaymentRequest.class);
        verify(transactionService).utilPayment(captor.capture());
        assertThat(captor.getValue()).isEqualTo(request);
    }

    @Test
    void fundTransfer_insufficientFunds_returns400WithErrorCode() throws Exception {
        // Arrange
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100);
        when(transactionService.fundTransfer(any()))
            .thenThrow(new InsufficientFundsException("Insufficient funds", GlobalErrorCode.INSUFFICIENT_FUNDS));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void utilPayment_insufficientFunds_returns400WithErrorCode() throws Exception {
        // Arrange
        UtilityPaymentRequest request =
            aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, 40);
        when(transactionService.utilPayment(any()))
            .thenThrow(new InsufficientFundsException("Insufficient funds", GlobalErrorCode.INSUFFICIENT_FUNDS));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/util-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void fundTransfer_entityNotFound_returns400WithErrorCode() throws Exception {
        // Arrange
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100);
        when(transactionService.fundTransfer(any())).thenThrow(new EntityNotFoundException());

        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void fundTransfer_unexpectedException_returns400WithGenericMessage() throws Exception {
        // Arrange
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100);
        when(transactionService.fundTransfer(any())).thenThrow(new RuntimeException("boom"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith("Exception occur inside API")));
    }

    @Test
    void fundTransfer_malformedJson_returns400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void fundTransfer_wrongTypeAmount_returns400() throws Exception {
        // Arrange
        String body = "{\"fromAccount\":\"100015003000\",\"toAccount\":\"100015003001\",\"amount\":\"abc\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void fundTransfer_missingContentType_returns415() throws Exception {
        // Arrange
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100);

        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(transactionService);
    }

    @Disabled("Divergence: request DTOs have no Bean Validation; tracked in Phase 1 PR")
    @Test
    void fundTransfer_emptyBody_returns400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Disabled("Divergence: SimpleBankingGlobalException @AllArgsConstructor is (code, message) but subclasses call super(message, code), so code/message are swapped; tracked in Phase 1 PR")
    @Test
    void fundTransfer_insufficientFunds_returnsErrorCodeInCodeField() throws Exception {
        // Arrange
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100);
        when(transactionService.fundTransfer(any()))
            .thenThrow(new InsufficientFundsException("Insufficient funds", GlobalErrorCode.INSUFFICIENT_FUNDS));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INSUFFICIENT_FUNDS))
            .andExpect(jsonPath("$.message").value("Insufficient funds"));
    }
}
