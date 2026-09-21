package com.javatodev.finance.configuration.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppAuthUserFilterTest {

    private final AppAuthUserFilter filter = new AppAuthUserFilter();

    @AfterEach
    void clearContext() {
        ApiRequestContextHolder.clearContext();
    }

    @Test
    void doFilter_headerPresent_exposesAndThenClearsAuthId() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Id", "auth-user-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideChain = new AtomicReference<>();

        // Act
        filter.doFilter(request, response, (servletRequest, servletResponse) ->
            insideChain.set(ApiRequestContextHolder.getContext().getAuthId()));

        // Assert
        assertThat(insideChain).hasValue("auth-user-0001");
        assertThat(ApiRequestContextHolder.getContext().getAuthId()).isNull();
    }

    @Test
    void doFilter_headerAbsent_leavesAuthIdNull() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideChain = new AtomicReference<>();

        // Act
        filter.doFilter(request, response, (servletRequest, servletResponse) ->
            insideChain.set(ApiRequestContextHolder.getContext().getAuthId()));

        // Assert
        assertThat(insideChain).hasValue(null);
    }

    @Test
    void doFilter_blankHeader_leavesAuthIdNull() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Id", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideChain = new AtomicReference<>();

        // Act
        filter.doFilter(request, response, (servletRequest, servletResponse) ->
            insideChain.set(ApiRequestContextHolder.getContext().getAuthId()));

        // Assert
        assertThat(insideChain).hasValue(null);
    }

    @Test
    void doFilter_chainThrows_propagatesAndClearsContext() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Id", "auth-user-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException exception = new ServletException("boom");

        // Act & Assert
        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw exception;
        })).isSameAs(exception);
        assertThat(ApiRequestContextHolder.getContext().getAuthId()).isNull();
    }
}
