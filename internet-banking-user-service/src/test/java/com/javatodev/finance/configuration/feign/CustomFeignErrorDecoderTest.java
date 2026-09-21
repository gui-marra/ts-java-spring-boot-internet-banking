package com.javatodev.finance.configuration.feign;

import com.javatodev.finance.exception.SimpleBankingGlobalException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomFeignErrorDecoderTest {
    private final CustomFeignErrorDecoder decoder = new CustomFeignErrorDecoder();

    private Response response(int status, String body) {
        return Response.builder()
            .status(status)
            .reason("x")
            .request(Request.create(Request.HttpMethod.GET, "/api/v1/user/808829932V",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
            .headers(Collections.emptyMap())
            .body(body, StandardCharsets.UTF_8)
            .build();
    }

    @Test
    void decode_badRequestWithJson_returnsBankingException() {
        Exception exception = decoder.decode("key", response(400,
            "{\"code\":\"CORE-SERVICE-1001\",\"message\":\"User not found under given identification\"}"));

        assertThat(exception).isInstanceOf(SimpleBankingGlobalException.class)
            .extracting("code").isEqualTo("CORE-SERVICE-1001");
        assertThat(exception).hasMessage("User not found under given identification");
    }

    @Test
    void decode_badRequestWithUnknownField_stillDecodes() {
        Exception exception = decoder.decode("key", response(400,
            "{\"code\":\"CORE-SERVICE-1001\",\"message\":\"User not found under given identification\",\"timestamp\":1}"));

        assertThat(exception).isInstanceOf(SimpleBankingGlobalException.class)
            .extracting("code").isEqualTo("CORE-SERVICE-1001");
    }

    @Test
    void decode_unauthorized_returnsUnauthorizedException() {
        Exception exception = decoder.decode("key", response(401, "{\"message\":\"no\"}"));

        assertThat(exception).isInstanceOf(Exception.class)
            .hasMessage("Unauthorized Request Through Feign")
            .isNotInstanceOf(SimpleBankingGlobalException.class);
    }

    @Test
    void decode_notFound_returnsUnidentifiedException() {
        assertThat(decoder.decode("key", response(404, "{\"message\":\"no\"}")))
            .hasMessage("Unidentified Request Through Feign");
    }

    @Test
    void decode_serverError_returnsCommonException() {
        assertThat(decoder.decode("key", response(500, "{\"message\":\"no\"}")))
            .hasMessage("Common Feign Exception");
    }

    @Test
    void decode_serviceUnavailable_returnsCommonException() {
        assertThat(decoder.decode("key", response(503, "{\"message\":\"no\"}")))
            .hasMessage("Common Feign Exception");
    }

    @Disabled("400 with non-JSON body makes decoder return null / NPE — see issue #21")
    @Test
    void decode_badRequestWithNonJson_returnsException() {
        assertThat(decoder.decode("key", response(400, "not json"))).isNotNull();
    }

    @Disabled("401 with no response body causes decoder NPE — see issue #21")
    @Test
    void decode_unauthorizedWithNoBody_returnsExceptionWithoutThrowing() {
        Response response = Response.builder()
            .status(401)
            .reason("x")
            .request(Request.create(Request.HttpMethod.GET, "/api/v1/user/808829932V",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
            .headers(Collections.emptyMap())
            .build();

        assertThatCode(() -> decoder.decode("key", response)).doesNotThrowAnyException();
        assertThat(decoder.decode("key", response)).hasMessage("Unauthorized Request Through Feign");
    }
}
