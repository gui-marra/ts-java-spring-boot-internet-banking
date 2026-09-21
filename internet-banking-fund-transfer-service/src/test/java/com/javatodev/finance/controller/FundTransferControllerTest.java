package com.javatodev.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.configuration.filter.ApiRequestContextHolder;
import com.javatodev.finance.configuration.filter.AppAuthUserFilter;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.service.FundTransferService;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.javatodev.finance.fixture.FundTransferFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.FundTransferFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.FundTransferFixtures.AUTH_ID;
import static com.javatodev.finance.fixture.FundTransferFixtures.TRANSACTION_ID;
import static com.javatodev.finance.fixture.FundTransferFixtures.aFundTransfer;
import static com.javatodev.finance.fixture.FundTransferFixtures.aFundTransferRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FundTransferController.class)
@Import(FundTransferControllerTest.FilterConfig.class)
class FundTransferControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private FundTransferService fundTransferService;

    @AfterEach
    void clearContext() {
        ApiRequestContextHolder.clearContext();
    }

    @Test
    void sendFundTransfer_validRequest_returns200WithTransactionResponse() throws Exception {
        // Arrange
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100);
        FundTransferResponse response = new FundTransferResponse();
        response.setTransactionId(TRANSACTION_ID);
        response.setMessage("Fund Transfer Successfully Completed");
        when(fundTransferService.fundTransfer(any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId").value(TRANSACTION_ID))
            .andExpect(jsonPath("$.message").value("Fund Transfer Successfully Completed"));

        ArgumentCaptor<FundTransferRequest> captor = ArgumentCaptor.forClass(FundTransferRequest.class);
        verify(fundTransferService).fundTransfer(captor.capture());
        assertThat(captor.getValue()).isEqualTo(request);
    }

    @Test
    void sendFundTransfer_authHeader_passesAuthIdThroughContext() throws Exception {
        // Arrange
        FundTransferResponse response = new FundTransferResponse();
        response.setTransactionId(TRANSACTION_ID);
        AtomicReference<String> seenAuthId = new AtomicReference<>();
        doAnswer(invocation -> {
            seenAuthId.set(ApiRequestContextHolder.getContext().getAuthId());
            return response;
        }).when(fundTransferService).fundTransfer(any());

        // Act
        mockMvc.perform(post("/api/v1/transfer")
                .header("X-Auth-Id", AUTH_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aFundTransferRequest(
                    ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100))))
            .andExpect(status().isOk());

        // Assert
        assertThat(seenAuthId).hasValue(AUTH_ID);
    }

    @Test
    void sendFundTransfer_withoutAuthHeader_passesNullAuthIdThroughContext() throws Exception {
        // Arrange
        FundTransferResponse response = new FundTransferResponse();
        response.setTransactionId(TRANSACTION_ID);
        AtomicReference<String> seenAuthId = new AtomicReference<>();
        doAnswer(invocation -> {
            seenAuthId.set(ApiRequestContextHolder.getContext().getAuthId());
            return response;
        }).when(fundTransferService).fundTransfer(any());

        // Act
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aFundTransferRequest(
                    ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100))))
            .andExpect(status().isOk());

        // Assert
        assertThat(seenAuthId).hasValue(null);
    }

    @Test
    void sendFundTransfer_malformedJson_returns400WithErrorBody() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Failed to read request"))
            .andExpect(jsonPath("$.instance").value("/api/v1/transfer"));

        // Assert
        verifyNoInteractions(fundTransferService);
    }

    @Test
    void sendFundTransfer_textPlain_returns415() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain text"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Unsupported Media Type"))
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.detail").value("Content-Type 'text/plain;charset=UTF-8' is not supported."))
            .andExpect(jsonPath("$.instance").value("/api/v1/transfer"));

        // Assert
        verifyNoInteractions(fundTransferService);
    }

    @Test
    void sendFundTransfer_simpleBankingException_returns400ErrorResponse() throws Exception {
        // Arrange
        when(fundTransferService.fundTransfer(any()))
            .thenThrow(new SimpleBankingGlobalException("CODE", "msg"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aFundTransferRequest(
                    ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CODE"))
            .andExpect(jsonPath("$.message").value("msg"));
    }

    @Test
    void sendFundTransfer_runtimeException_returns400GenericMessage() throws Exception {
        // Arrange
        when(fundTransferService.fundTransfer(any())).thenThrow(new RuntimeException("boom"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aFundTransferRequest(
                    ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100))))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith("Exception occur inside API")));
    }

    @Test
    void sendFundTransfer_feignException_returns400GenericMessage() throws Exception {
        // Arrange
        FeignException exception = new FeignException.BadRequest(
            "bad",
            Request.create(Request.HttpMethod.POST, "/x", Map.of(), null, new RequestTemplate()),
            null,
            Map.of());
        when(fundTransferService.fundTransfer(any())).thenThrow(exception);

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aFundTransferRequest(
                    ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100))))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith("Exception occur inside API")));
    }

    @Test
    void readFundTransfers_sortedPage_returns200WithTransfersAndPageable() throws Exception {
        // Arrange
        FundTransfer first = aFundTransfer(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100, TransactionStatus.SUCCESS);
        FundTransfer second = aFundTransfer(2L, ACCOUNT_NUMBER_2, ACCOUNT_NUMBER_1, 40, TransactionStatus.PENDING);
        when(fundTransferService.readAllTransfers(any())).thenReturn(List.of(first, second));

        // Act & Assert
        mockMvc.perform(get("/api/v1/transfer?page=1&size=5&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].fromAccount").value(ACCOUNT_NUMBER_1))
            .andExpect(jsonPath("$[0].toAccount").value(ACCOUNT_NUMBER_2))
            .andExpect(jsonPath("$[0].amount").value(100))
            .andExpect(jsonPath("$[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$[0].transactionReference").value(TRANSACTION_ID))
            .andExpect(jsonPath("$[1].id").value(2))
            .andExpect(jsonPath("$[1].amount").value(40));

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
            ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(fundTransferService).readAllTransfers(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("id")));
    }

    @Test
    void readFundTransfers_emptyResult_returnsEmptyArray() throws Exception {
        // Arrange
        when(fundTransferService.readAllTransfers(any())).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/transfer?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void readFundTransfers_defaultPageable_usesPageZeroAndSizeTwenty() throws Exception {
        // Arrange
        when(fundTransferService.readAllTransfers(any())).thenReturn(List.of());

        // Act
        mockMvc.perform(get("/api/v1/transfer")).andExpect(status().isOk());

        // Assert
        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
            ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(fundTransferService).readAllTransfers(pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 20));
    }

    @Test
    void sendFundTransfer_amountAsJsonString_returns200WithBigDecimalAmount() throws Exception {
        // Arrange
        FundTransferResponse response = new FundTransferResponse();
        response.setTransactionId(TRANSACTION_ID);
        when(fundTransferService.fundTransfer(any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccount\":\"100015003000\",\"toAccount\":\"100015003001\",\"amount\":\"100\",\"authID\":\"auth-user-0001\"}"))
            .andExpect(status().isOk());

        ArgumentCaptor<FundTransferRequest> request = ArgumentCaptor.forClass(FundTransferRequest.class);
        verify(fundTransferService).fundTransfer(request.capture());
        assertThat(request.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Disabled("no Bean Validation on FundTransferRequest — see issue #27")
    @Test
    void sendFundTransfer_negativeAmount_returns400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccount\":\"100015003000\",\"toAccount\":\"100015003001\",\"amount\":-5}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sendFundTransfer_unknownJsonField_isAccepted() throws Exception {
        // Arrange
        FundTransferResponse response = new FundTransferResponse();
        response.setTransactionId(TRANSACTION_ID);
        when(fundTransferService.fundTransfer(any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccount\":\"100015003000\",\"toAccount\":\"100015003001\",\"amount\":100,\"unknown\":\"ignored\"}"))
            .andExpect(status().isOk());
    }

    @TestConfiguration
    static class FilterConfig {
        @Bean
        FilterRegistrationBean<AppAuthUserFilter> authUserFilter() {
            FilterRegistrationBean<AppAuthUserFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new AppAuthUserFilter());
            registration.addUrlPatterns("/api/*");
            return registration;
        }
    }
}
