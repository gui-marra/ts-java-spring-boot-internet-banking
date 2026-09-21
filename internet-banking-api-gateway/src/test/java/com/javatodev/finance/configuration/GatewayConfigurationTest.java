package com.javatodev.finance.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GatewayConfigurationTest {

    @Mock
    GatewayFilterChain chain;

    GlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayConfiguration().customGlobalFilter();
    }

    private ServerWebExchange exchangeWith(Principal principal, MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.builder(request.build()).build();
        if (principal != null) {
            return exchange.mutate().principal(Mono.just(principal)).build();
        }
        return exchange;
    }

    private ServerWebExchange captureFilteredExchange(ServerWebExchange exchange) {
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }

    @Test
    void filter_authenticatedPrincipal_addsAuthIdHeader() {
        Principal principal = () -> "abc";

        ServerWebExchange filtered = captureFilteredExchange(
            exchangeWith(principal, MockServerHttpRequest.get("/user/api/v1/x").header("X-Existing", "keep")));

        assertThat(filtered.getRequest().getHeaders().getFirst("X-Auth-Id")).isEqualTo("abc");
    }

    @Test
    void filter_authenticationPrincipal_addsAuthIdHeader() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("abc");

        ServerWebExchange filtered = captureFilteredExchange(
            exchangeWith(authentication, MockServerHttpRequest.get("/user/api/v1/x")));

        assertThat(filtered.getRequest().getHeaders().getFirst("X-Auth-Id")).isEqualTo("abc");
    }

    @Test
    void filter_anonymousPrincipal_addsSystemUserHeader() {
        ServerWebExchange filtered = captureFilteredExchange(
            exchangeWith(null, MockServerHttpRequest.get("/user/api/v1/x")));

        assertThat(filtered.getRequest().getHeaders().getFirst("X-Auth-Id")).isEqualTo("SYSTEM USER");
    }

    @Test
    void filter_anyRequest_preservesExistingHeadersAndPath() {
        Principal principal = () -> "abc";

        ServerWebExchange filtered = captureFilteredExchange(
            exchangeWith(principal, MockServerHttpRequest.get("/user/api/v1/x").header("X-Existing", "keep")));

        assertThat(filtered.getRequest().getHeaders().getFirst("X-Existing")).isEqualTo("keep");
        assertThat(filtered.getRequest().getPath().value()).isEqualTo("/user/api/v1/x");
        assertThat(filtered.getRequest().getMethod().name()).isEqualTo("GET");
    }

    @Test
    void filter_clientSuppliedAuthId_isReplacedByPrincipalName() {
        Principal principal = () -> "abc";

        ServerWebExchange filtered = captureFilteredExchange(
            exchangeWith(principal, MockServerHttpRequest.get("/user/api/v1/x").header("X-Auth-Id", "forged")));

        assertThat(filtered.getRequest().getHeaders().get("X-Auth-Id")).hasSize(1);
        assertThat(filtered.getRequest().getHeaders().getFirst("X-Auth-Id")).isEqualTo("abc");
    }

    @Test
    void filter_chainError_propagatesError() {
        when(chain.filter(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(filter.filter(
                exchangeWith(() -> "abc", MockServerHttpRequest.get("/user/api/v1/x")), chain))
            .expectError(IllegalStateException.class)
            .verify();
    }

    @Test
    void filter_emptyPrincipalName_setsEmptyHeader() {
        ServerWebExchange filtered = captureFilteredExchange(
            exchangeWith(() -> "", MockServerHttpRequest.get("/user/api/v1/x")));

        assertThat(filtered.getRequest().getHeaders().getFirst("X-Auth-Id")).isEqualTo("");
    }
}
