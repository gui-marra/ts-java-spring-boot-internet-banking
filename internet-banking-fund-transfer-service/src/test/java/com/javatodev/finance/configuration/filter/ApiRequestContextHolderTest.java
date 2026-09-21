package com.javatodev.finance.configuration.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRequestContextHolderTest {

    @AfterEach
    void clearContext() {
        ApiRequestContextHolder.clearContext();
    }

    @Test
    void getContext_calledTwice_returnsSameInstance() {
        // Act
        ApiRequestContext first = ApiRequestContextHolder.getContext();
        ApiRequestContext second = ApiRequestContextHolder.getContext();

        // Assert
        assertThat(second).isSameAs(first);
    }

    @Test
    void clearContext_thenGetContext_returnsNewInstance() {
        // Arrange
        ApiRequestContext first = ApiRequestContextHolder.getContext();

        // Act
        ApiRequestContextHolder.clearContext();
        ApiRequestContext second = ApiRequestContextHolder.getContext();

        // Assert
        assertThat(second).isNotSameAs(first);
    }
}
