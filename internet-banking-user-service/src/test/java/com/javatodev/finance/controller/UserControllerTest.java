package com.javatodev.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.configuration.filter.ApiRequestContextHolder;
import com.javatodev.finance.configuration.filter.AppAuthUserFilter;
import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.fixture.UserFixtures;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.UserUpdateRequest;
import com.javatodev.finance.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.javatodev.finance.fixture.UserFixtures.AUTH_ID;
import static com.javatodev.finance.fixture.UserFixtures.aUser;
import static com.javatodev.finance.fixture.UserFixtures.anUpdateRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@org.springframework.context.annotation.Import(UserControllerTest.FilterConfig.class)
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private UserService userService;

    @AfterEach
    void clearRequestContext() {
        ApiRequestContextHolder.clearContext();
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

    @Test
    void createUser_validRequest_returns200WithUserJson() throws Exception {
        User request = aUser();
        when(userService.createUser(any())).thenReturn(UserFixtures.aUser(1L, AUTH_ID,
            com.javatodev.finance.model.dto.Status.PENDING));

        mockMvc.perform(post("/api/v1/bank-users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.authId").value(AUTH_ID))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.identification").value(request.getIdentification()))
            .andExpect(jsonPath("$.email").value(request.getEmail()));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).createUser(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(request);
    }

    @Test
    void createUser_serviceThrowsGlobalException_returns400WithCodeAndMessage() throws Exception {
        SimpleBankingGlobalException exception = new SimpleBankingGlobalException();
        exception.setCode("USER-SERVICE-1001");
        exception.setMessage("dup");
        when(userService.createUser(any())).thenThrow(exception);

        mockMvc.perform(post("/api/v1/bank-users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUser())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("USER-SERVICE-1001"))
            .andExpect(jsonPath("$.message").value("dup"));
    }

    @Test
    void createUser_serviceThrowsEntityNotFound_returns400() throws Exception {
        when(userService.createUser(any())).thenThrow(new EntityNotFoundException());

        mockMvc.perform(post("/api/v1/bank-users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUser())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").isNotEmpty())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #17")
    @Test
    void createUser_serviceThrowsEntityNotFound_returnsExpectedCode() throws Exception {
        when(userService.createUser(any())).thenThrow(new EntityNotFoundException());

        mockMvc.perform(post("/api/v1/bank-users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUser())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND));
    }

    @Test
    void createUser_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/bank-users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    void createUser_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/bank-users/register")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    void updateUser_approved_returns200WithUpdatedStatus() throws Exception {
        when(userService.updateUser(any(), any())).thenReturn(
            UserFixtures.aUser(1L, AUTH_ID, com.javatodev.finance.model.dto.Status.APPROVED));

        mockMvc.perform(patch("/api/v1/bank-users/update/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(anUpdateRequest(
                    com.javatodev.finance.model.dto.Status.APPROVED))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<UserUpdateRequest> requestCaptor = ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(userService).updateUser(idCaptor.capture(), requestCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(1L);
        assertThat(requestCaptor.getValue().getStatus())
            .isEqualTo(com.javatodev.finance.model.dto.Status.APPROVED);
    }

    @Test
    void updateUser_unknownStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/bank-users/update/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"NOPE\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    void updateUser_nonNumericId_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/bank-users/update/abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    void readUsers_pageParams_passesPageableToService() throws Exception {
        when(userService.readUsers(any())).thenReturn(List.of(aUser(), aUser(2L, "A2",
            com.javatodev.finance.model.dto.Status.APPROVED)));

        mockMvc.perform(get("/api/v1/bank-users?page=0&size=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].email").value("sam@gmail.com"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).readUsers(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void readUsers_noParams_defaultsToPage0Size20() throws Exception {
        when(userService.readUsers(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bank-users")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).readUsers(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void readUser_found_returns200() throws Exception {
        when(userService.readUser(1L)).thenReturn(aUser(1L, AUTH_ID,
            com.javatodev.finance.model.dto.Status.PENDING));

        mockMvc.perform(get("/api/v1/bank-users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.authId").value(AUTH_ID));
    }

    @Test
    void readUser_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/bank-users/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    void readUser_notFound_returns400() throws Exception {
        when(userService.readUser(1L)).thenThrow(new EntityNotFoundException());

        mockMvc.perform(get("/api/v1/bank-users/1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").isNotEmpty())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createUser_withAuthHeader_exposesAuthIdInRequestContext() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(ApiRequestContextHolder.getContext().getAuthId());
            return UserFixtures.aUser(1L, AUTH_ID, com.javatodev.finance.model.dto.Status.PENDING);
        }).when(userService).createUser(any());

        mockMvc.perform(post("/api/v1/bank-users/register")
                .header("X-Auth-Id", "kc-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUser())))
            .andExpect(status().isOk());

        assertThat(seen).hasValue("kc-123");
        assertThat(ApiRequestContextHolder.getContext().getAuthId()).isNull();
    }

    @Test
    void createUser_withoutAuthHeader_contextAuthIdIsNull() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(ApiRequestContextHolder.getContext().getAuthId());
            return UserFixtures.aUser(1L, AUTH_ID, com.javatodev.finance.model.dto.Status.PENDING);
        }).when(userService).createUser(any());

        mockMvc.perform(post("/api/v1/bank-users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUser())))
            .andExpect(status().isOk());

        assertThat(seen).hasValue(null);
    }

    @Test
    void createUser_serviceThrowsRuntimeException_returns400WithGenericBody() throws Exception {
        when(userService.createUser(any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/v1/bank-users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aUser())))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.startsWith("Exception occur inside API")));
    }
}
