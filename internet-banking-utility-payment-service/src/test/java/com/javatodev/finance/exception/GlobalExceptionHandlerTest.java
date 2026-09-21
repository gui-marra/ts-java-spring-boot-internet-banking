package com.javatodev.finance.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void handleGlobalException_simpleBankingException_returnsBadRequestErrorResponse() {
        // Arrange
        SimpleBankingGlobalException exception = new SimpleBankingGlobalException("CODE", "msg");

        // Act
        ResponseEntity<?> response = exceptionHandler.handleGlobalException(exception, Locale.ENGLISH);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body.getCode()).isEqualTo("CODE");
        assertThat(body.getMessage()).isEqualTo("msg");
    }

    @Test
    void handleException_unexpectedException_returnsBadRequestGenericMessage() {
        // Arrange
        IllegalStateException exception = new IllegalStateException("boom");

        // Act
        ResponseEntity<?> response = exceptionHandler.handleException(exception, Locale.ENGLISH);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat((String) response.getBody())
            .startsWith("Exception occur inside API ")
            .contains("boom");
    }
}
