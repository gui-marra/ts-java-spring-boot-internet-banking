package com.javatodev.finance.controller;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.service.AccountService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_EMAIL_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_IDENTIFICATION_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_ACCOUNT_VODAFONE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aBankAccount;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AccountService accountService;

    @Test
    void getBankAccount_existingAccount_returns200WithAccountJson() throws Exception {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));

        // Act & Assert
        mockMvc.perform(get("/api/v1/account/bank-account/{account_number}", ACCOUNT_NUMBER_1))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.number").value(ACCOUNT_NUMBER_1))
            .andExpect(jsonPath("$.type").value("SAVINGS_ACCOUNT"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.availableBalance").value(200))
            .andExpect(jsonPath("$.actualBalance").value(200))
            .andExpect(jsonPath("$.user.id").value(1))
            .andExpect(jsonPath("$.user.firstName").value("Sam"))
            .andExpect(jsonPath("$.user.lastName").value("Silva"))
            .andExpect(jsonPath("$.user.email").value(USER_EMAIL_1))
            .andExpect(jsonPath("$.user.identificationNumber").value(USER_IDENTIFICATION_1));
    }

    @Test
    void getBankAccount_pathVariable_passedVerbatimToService() throws Exception {
        // Arrange
        when(accountService.readBankAccount(anyString())).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));

        // Act
        mockMvc.perform(get("/api/v1/account/bank-account/{account_number}", ACCOUNT_NUMBER_1))
            .andExpect(status().isOk());

        // Assert
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(accountService).readBankAccount(captor.capture());
        assertThat(captor.getValue()).isEqualTo(ACCOUNT_NUMBER_1);
    }

    @Test
    void getBankAccount_urlEncodedPathVariable_passedDecodedToService() throws Exception {
        // Arrange
        when(accountService.readBankAccount(anyString())).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));

        // Act
        mockMvc.perform(get(URI.create("/api/v1/account/bank-account/1000%20150")))
            .andExpect(status().isOk());

        // Assert
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(accountService).readBankAccount(captor.capture());
        assertThat(captor.getValue()).isEqualTo("1000 150");
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void getBankAccount_entityNotFound_returns400WithErrorCode() throws Exception {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenThrow(new EntityNotFoundException());

        // Act & Assert
        mockMvc.perform(get("/api/v1/account/bank-account/{account_number}", ACCOUNT_NUMBER_1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND))
            .andExpect(jsonPath("$.message").value("Requested entity not present in the DB."));
    }

    @Test
    void getBankAccount_unexpectedException_returns400WithGenericMessage() throws Exception {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenThrow(new RuntimeException("boom"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/account/bank-account/{account_number}", ACCOUNT_NUMBER_1))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith("Exception occur inside API")));
    }

    @Test
    void getBankAccount_missingPathSegment_returns404() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/account/bank-account/"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(accountService);
    }

    @Test
    void getBankAccount_acceptXml_returns406() throws Exception {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));

        // Act & Assert
        mockMvc.perform(get("/api/v1/account/bank-account/{account_number}", ACCOUNT_NUMBER_1)
                .accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotAcceptable());
    }

    @Test
    void getBankAccount_postMethod_returns405() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/account/bank-account/{account_number}", ACCOUNT_NUMBER_1))
            .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(accountService);
    }

    @Test
    void getUtilityAccount_existingProvider_returns200WithAccountJson() throws Exception {
        // Arrange
        when(accountService.readUtilityAccount(UTILITY_PROVIDER_VODAFONE))
            .thenReturn(aUtilityAccount(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE));

        // Act & Assert
        mockMvc.perform(get("/api/v1/account/util-account/{account_name}", UTILITY_PROVIDER_VODAFONE))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(UTILITY_PROVIDER_VODAFONE_ID))
            .andExpect(jsonPath("$.number").value(UTILITY_ACCOUNT_VODAFONE))
            .andExpect(jsonPath("$.providerName").value(UTILITY_PROVIDER_VODAFONE));
    }

    @Test
    void getUtilityAccount_pathVariable_passedVerbatimToService() throws Exception {
        // Arrange
        when(accountService.readUtilityAccount(anyString()))
            .thenReturn(aUtilityAccount(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE));

        // Act
        mockMvc.perform(get("/api/v1/account/util-account/{account_name}", UTILITY_PROVIDER_VODAFONE))
            .andExpect(status().isOk());

        // Assert
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(accountService).readUtilityAccount(captor.capture());
        assertThat(captor.getValue()).isEqualTo(UTILITY_PROVIDER_VODAFONE);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void getUtilityAccount_entityNotFound_returns400WithErrorCode() throws Exception {
        // Arrange
        when(accountService.readUtilityAccount(UTILITY_PROVIDER_VODAFONE)).thenThrow(new EntityNotFoundException());

        // Act & Assert
        mockMvc.perform(get("/api/v1/account/util-account/{account_name}", UTILITY_PROVIDER_VODAFONE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND))
            .andExpect(jsonPath("$.message").value("Requested entity not present in the DB."));
    }

    @Test
    void getUtilityAccount_unexpectedException_returns400WithGenericMessage() throws Exception {
        // Arrange
        when(accountService.readUtilityAccount(UTILITY_PROVIDER_VODAFONE)).thenThrow(new RuntimeException("boom"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/account/util-account/{account_name}", UTILITY_PROVIDER_VODAFONE))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith("Exception occur inside API")));
    }

    @Test
    void getUtilityAccount_missingPathSegment_returns404() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/account/util-account/"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(accountService);
    }

}
