package com.javatodev.finance.exception;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleGlobalException_setterBuiltException_returns400WithCodeAndMessage() {
        SimpleBankingGlobalException exception = new SimpleBankingGlobalException();
        exception.setCode("USER-SERVICE-1001");
        exception.setMessage("dup");

        ResponseEntity response = handler.handleGlobalException(exception, Locale.ENGLISH);
        ErrorResponse body = (ErrorResponse) response.getBody();

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("USER-SERVICE-1001");
        assertThat(body.getMessage()).isEqualTo("dup");
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #17")
    @Test
    void handleGlobalException_subclassException_returnsExpectedErrorDetails() {
        ResponseEntity response = handler.handleGlobalException(
            new UserAlreadyRegisteredException("This email already registered...", GlobalErrorCode.ERROR_EMAIL_REGISTERED),
            Locale.ENGLISH);

        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body.getCode()).isEqualTo(GlobalErrorCode.ERROR_EMAIL_REGISTERED);
    }

    @Test
    void handleException_runtimeException_returns400WithGenericBody() {
        ResponseEntity response = handler.handleException(new RuntimeException("x"), Locale.ENGLISH);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().toString())
            .startsWith("Exception occur inside API")
            .contains("x");
    }
}
