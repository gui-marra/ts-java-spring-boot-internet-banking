package com.javatodev.finance.exception;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleGlobalException_simpleBankingException_returns400ErrorResponse() {
        // Act
        ResponseEntity<?> response = handler.handleGlobalException(
            new SimpleBankingGlobalException("CODE", "msg"), Locale.ENGLISH);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body).extracting(ErrorResponse::getCode, ErrorResponse::getMessage)
            .containsExactly("CODE", "msg");
    }

    @Test
    void handleException_runtimeException_returns400GenericMessage() {
        // Act
        ResponseEntity<?> response = handler.handleException(new IllegalStateException("boom"), Locale.ENGLISH);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat((String) response.getBody()).startsWith("Exception occur inside API");
    }

    @Test
    void handleException_feignException_returns400GenericMessage() {
        // Arrange
        FeignException exception = new FeignException.BadRequest(
            "bad",
            Request.create(Request.HttpMethod.POST, "/x", Map.of(), null, new RequestTemplate()),
            null,
            Map.of());

        // Act
        ResponseEntity<?> response = handler.handleException(exception, Locale.ENGLISH);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat((String) response.getBody()).startsWith("Exception occur inside API");
    }
}
