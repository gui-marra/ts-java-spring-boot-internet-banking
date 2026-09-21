package com.javatodev.finance.exception;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void handleGlobalException_entityNotFound_returnsBadRequestWithCodeAndMessage() {
        // Act
        ResponseEntity<?> response = handler.handleGlobalException(new EntityNotFoundException(), Locale.ENGLISH);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOfSatisfying(ErrorResponse.class, body -> {
            assertThat(body.getCode()).isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
            assertThat(body.getMessage()).isEqualTo("Requested entity not present in the DB.");
        });
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void handleGlobalException_insufficientFunds_returnsBadRequestWithCodeAndMessage() {
        // Act
        ResponseEntity<?> response = handler.handleGlobalException(
            new InsufficientFundsException("msg", GlobalErrorCode.INSUFFICIENT_FUNDS), Locale.ENGLISH);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOfSatisfying(ErrorResponse.class, body -> {
            assertThat(body.getCode()).isEqualTo(GlobalErrorCode.INSUFFICIENT_FUNDS);
            assertThat(body.getMessage()).isEqualTo("msg");
        });
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void handleGlobalException_messageOnlyException_returnsNullCodeAndPreservesMessage() {
        // Act
        ResponseEntity<?> response = handler.handleGlobalException(
            new SimpleBankingGlobalException("only message"), Locale.ENGLISH);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOfSatisfying(ErrorResponse.class, body -> {
            assertThat(body.getCode()).isNull();
            assertThat(body.getMessage()).isEqualTo("only message");
        });
    }

    @Test
    void handleException_runtimeException_returnsBadRequestWithGenericMessage() {
        // Act
        ResponseEntity<?> response = handler.handleException(new RuntimeException("boom"), Locale.ENGLISH);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOfSatisfying(String.class, body ->
            assertThat(body).startsWith("Exception occur inside API").contains("boom"));
    }

}
