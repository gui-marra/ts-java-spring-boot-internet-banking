package com.javatodev.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.configuration.filter.ApiRequestContextHolder;
import com.javatodev.finance.configuration.filter.AppAuthUserFilter;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.service.UtilityPaymentService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.CORE_TRANSACTION_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.REFERENCE_NUMBER;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aCoreFeignException;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aCoreUtilityPaymentResponse;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPayment;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPaymentRequest;
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

@WebMvcTest(UtilityPaymentController.class)
@Import(UtilityPaymentControllerTest.FilterConfig.class)
class UtilityPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private UtilityPaymentService utilityPaymentService;

    @Test
    void processPayment_validJson_returns200WithResponse() throws Exception {
        // Arrange
        UtilityPaymentRequest request = aUtilityPaymentRequest();
        when(utilityPaymentService.utilPayment(any())).thenReturn(aCoreUtilityPaymentResponse());

        // Act & Assert
        mockMvc.perform(post("/api/v1/utility-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Utility Payment Successfully Processed"))
            .andExpect(jsonPath("$.transactionId").value(CORE_TRANSACTION_ID));

        ArgumentCaptor<UtilityPaymentRequest> captor = ArgumentCaptor.forClass(UtilityPaymentRequest.class);
        verify(utilityPaymentService).utilPayment(captor.capture());
        assertThat(captor.getValue()).isEqualTo(request);
    }

    @Test
    void processPayment_simpleBankingException_returns400WithErrorResponse() throws Exception {
        // Arrange
        when(utilityPaymentService.utilPayment(any()))
            .thenThrow(new SimpleBankingGlobalException("CODE", "msg"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/utility-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUtilityPaymentRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CODE"))
            .andExpect(jsonPath("$.message").value("msg"));
    }

    @Test
    void processPayment_feignException_returns400WithGenericMessage() throws Exception {
        // Arrange
        when(utilityPaymentService.utilPayment(any())).thenThrow(aCoreFeignException());

        // Act & Assert
        mockMvc.perform(post("/api/v1/utility-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUtilityPaymentRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith("Exception occur inside API")));
    }

    @Test
    void processPayment_malformedJson_returns400ProblemDetail() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/utility-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.detail").value("Failed to read request"))
            .andExpect(jsonPath("$.instance").value("/api/v1/utility-payment"));

        verifyNoInteractions(utilityPaymentService);
    }

    @Test
    void processPayment_missingContentType_returns415() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/utility-payment")
                .content(objectMapper.writeValueAsString(aUtilityPaymentRequest())))
            .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(utilityPaymentService);
    }

    @Test
    void readPayments_withPageAndSort_returnsDtosAndForwardsPageable() throws Exception {
        // Arrange
        when(utilityPaymentService.readPayments(any())).thenReturn(List.of(
            aUtilityPayment(TransactionStatus.SUCCESS),
            aUtilityPayment(TransactionStatus.PROCESSING)));

        // Act & Assert
        mockMvc.perform(get("/api/v1/utility-payment")
                .param("page", "1")
                .param("size", "5")
                .param("sort", "amount,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].providerId").value(UTILITY_PROVIDER_VODAFONE_ID))
            .andExpect(jsonPath("$[0].amount").value(250))
            .andExpect(jsonPath("$[0].referenceNumber").value(REFERENCE_NUMBER))
            .andExpect(jsonPath("$[0].account").value(ACCOUNT_NUMBER_1))
            .andExpect(jsonPath("$[0].status").value("SUCCESS"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(utilityPaymentService).readPayments(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort().getOrderFor("amount").getDirection())
            .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void readPayments_emptyList_returnsEmptyJsonArray() throws Exception {
        // Arrange
        when(utilityPaymentService.readPayments(any())).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/utility-payment"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void processPayment_authHeader_setsRequestContextForServiceCall() throws Exception {
        // Arrange
        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(ApiRequestContextHolder.getContext().getAuthId());
            return aCoreUtilityPaymentResponse();
        }).when(utilityPaymentService).utilPayment(any());

        // Act
        mockMvc.perform(post("/api/v1/utility-payment")
                .header("X-Auth-Id", "user-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUtilityPaymentRequest())))
            .andExpect(status().isOk());

        // Assert
        assertThat(seen).hasValue("user-123");
    }

    @Test
    void processPayment_missingAuthHeader_leavesRequestContextWithoutAuthId() throws Exception {
        // Arrange
        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(ApiRequestContextHolder.getContext().getAuthId());
            return aCoreUtilityPaymentResponse();
        }).when(utilityPaymentService).utilPayment(any());

        // Act
        mockMvc.perform(post("/api/v1/utility-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUtilityPaymentRequest())))
            .andExpect(status().isOk());

        // Assert
        assertThat(seen).hasValue(null);
    }

    @Disabled("Amount is not validated at the API — see issue #24")
    @Test
    void processPayment_negativeAmount_rejectsRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/utility-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":1,\"amount\":-1,\"referenceNumber\":\"REF-0001\",\"account\":\"100015003000\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").exists());

        verifyNoInteractions(utilityPaymentService);
    }

    @Disabled("Account is not validated at the API — see issue #24")
    @Test
    void processPayment_missingAccount_rejectsRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/utility-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":1,\"amount\":10,\"referenceNumber\":\"REF-0001\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").exists());

        verifyNoInteractions(utilityPaymentService);
    }

    @TestConfiguration
    static class FilterConfig {
        @Bean
        FilterRegistrationBean<AppAuthUserFilter> appAuthUserFilter() {
            FilterRegistrationBean<AppAuthUserFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new AppAuthUserFilter());
            registration.addUrlPatterns("/api/*");
            return registration;
        }
    }
}
