package com.javatodev.finance.controller;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.service.UserService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_EMAIL_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_IDENTIFICATION_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private UserService userService;

    @Test
    void readUser_existingUser_returns200WithUserJson() throws Exception {
        // Arrange
        when(userService.readUser(USER_IDENTIFICATION_1)).thenReturn(aUser(USER_IDENTIFICATION_1));

        // Act & Assert
        mockMvc.perform(get("/api/v1/user/{identification}", USER_IDENTIFICATION_1))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.firstName").value("Sam"))
            .andExpect(jsonPath("$.lastName").value("Silva"))
            .andExpect(jsonPath("$.email").value(USER_EMAIL_1))
            .andExpect(jsonPath("$.identificationNumber").value(USER_IDENTIFICATION_1))
            .andExpect(jsonPath("$.bankAccounts.length()").value(1))
            .andExpect(jsonPath("$.bankAccounts[0].number").value(ACCOUNT_NUMBER_1))
            .andExpect(jsonPath("$.bankAccounts[0].type").value("SAVINGS_ACCOUNT"))
            .andExpect(jsonPath("$.bankAccounts[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.bankAccounts[0].availableBalance").value(200))
            .andExpect(jsonPath("$.bankAccounts[0].actualBalance").value(200));
    }

    @Test
    void readUser_pathVariable_passedVerbatimToService() throws Exception {
        // Arrange
        when(userService.readUser(anyString())).thenReturn(aUser(USER_IDENTIFICATION_1));

        // Act
        mockMvc.perform(get("/api/v1/user/{identification}", USER_IDENTIFICATION_1))
            .andExpect(status().isOk());

        // Assert
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(userService).readUser(captor.capture());
        assertThat(captor.getValue()).isEqualTo(USER_IDENTIFICATION_1);
    }

    @Test
    void readUser_entityNotFound_returns400WithErrorCode() throws Exception {
        // Arrange
        when(userService.readUser(USER_IDENTIFICATION_1)).thenThrow(new EntityNotFoundException());

        // Act & Assert
        mockMvc.perform(get("/api/v1/user/{identification}", USER_IDENTIFICATION_1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND))
            .andExpect(jsonPath("$.message").value("Requested entity not present in the DB."));
    }

    @Test
    void readUser_unexpectedException_returns400WithGenericMessage() throws Exception {
        // Arrange
        when(userService.readUser(USER_IDENTIFICATION_1)).thenThrow(new RuntimeException("boom"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/user/{identification}", USER_IDENTIFICATION_1))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith("Exception occur inside API")));
    }

    @Test
    void readUsers_noParams_returns200WithUserArrayAndDefaultPageable() throws Exception {
        // Arrange
        when(userService.readUsers(any())).thenReturn(List.of(aUser(USER_IDENTIFICATION_1)));

        // Act
        mockMvc.perform(get("/api/v1/user"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].email").value(USER_EMAIL_1))
            .andExpect(jsonPath("$[0].identificationNumber").value(USER_IDENTIFICATION_1))
            .andExpect(jsonPath("$[0].bankAccounts[0].number").value(ACCOUNT_NUMBER_1));

        // Assert
        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.unsorted());
    }

    @Test
    void readUsers_pageSizeAndSortParams_passedToService() throws Exception {
        // Arrange
        when(userService.readUsers(any())).thenReturn(Collections.emptyList());

        // Act
        mockMvc.perform(get("/api/v1/user")
                .param("page", "2")
                .param("size", "5")
                .param("sort", "email,desc"))
            .andExpect(status().isOk());

        // Assert
        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "email"));
    }

    @Test
    void readUsers_emptyResult_returnsEmptyJsonArray() throws Exception {
        // Arrange
        when(userService.readUsers(any())).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/v1/user"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void readUsers_negativePage_fallsBackToPageZero() throws Exception {
        // Arrange
        when(userService.readUsers(any())).thenReturn(Collections.emptyList());

        // Act
        mockMvc.perform(get("/api/v1/user").param("page", "-1"))
            .andExpect(status().isOk());

        // Assert
        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void readUsers_zeroSize_fallsBackToDefaultSize() throws Exception {
        // Arrange
        when(userService.readUsers(any())).thenReturn(Collections.emptyList());

        // Act
        mockMvc.perform(get("/api/v1/user").param("size", "0"))
            .andExpect(status().isOk());

        // Assert
        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void readUsers_nonNumericSize_fallsBackToDefaultSize() throws Exception {
        // Arrange
        when(userService.readUsers(any())).thenReturn(Collections.emptyList());

        // Act
        mockMvc.perform(get("/api/v1/user").param("size", "abc"))
            .andExpect(status().isOk());

        // Assert
        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void readUsers_oversizedSize_clampedToMaxPageSize() throws Exception {
        // Arrange
        when(userService.readUsers(any())).thenReturn(Collections.emptyList());

        // Act
        mockMvc.perform(get("/api/v1/user").param("size", "5000"))
            .andExpect(status().isOk());

        // Assert
        Pageable pageable = capturePageable();
        assertThat(pageable.getPageSize()).isEqualTo(2000);
    }

    @Test
    void readUser_deleteMethod_returns405() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/v1/user/{identification}", "x"))
            .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(userService);
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).readUsers(captor.capture());
        return captor.getValue();
    }

}
