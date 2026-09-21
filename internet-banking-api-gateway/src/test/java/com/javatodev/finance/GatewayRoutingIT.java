package com.javatodev.finance;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@Tag("integration")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingIT {

    @Autowired
    WebTestClient webTestClient;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("wiremock.port", () -> DownstreamWireMock.SERVER.port());
    }

    @BeforeEach
    void resetDownstream() {
        DownstreamWireMock.SERVER.resetAll();
    }

    @ParameterizedTest
    @CsvSource({
        "/user/api/v1/bank-users/ping, user",
        "/fund-transfer/api/v1/fund-transfers/ping, fund-transfer",
        "/banking-core/api/v1/bank-accounts/ping, banking-core",
        "/utility-payment/api/v1/utility-payments/ping, utility-payment"
    })
    void route_prefixedPath_isProxiedToDownstream(String prefixedPath, String service) {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("abc")))
            .get().uri(prefixedPath)
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.service").isEqualTo(service);
    }

    @ParameterizedTest
    @CsvSource({
        "/user/api/v1/bank-users/ping, /api/v1/bank-users/ping",
        "/fund-transfer/api/v1/fund-transfers/ping, /api/v1/fund-transfers/ping",
        "/banking-core/api/v1/bank-accounts/ping, /api/v1/bank-accounts/ping",
        "/utility-payment/api/v1/utility-payments/ping, /api/v1/utility-payments/ping"
    })
    void route_prefixedPath_forwardsAuthIdHeaderWithPrincipal(String prefixedPath, String strippedPath) {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("abc")))
            .get().uri(prefixedPath)
            .exchange()
            .expectStatus().isOk();

        DownstreamWireMock.SERVER.verify(
            getRequestedFor(urlEqualTo(strippedPath)).withHeader("X-Auth-Id", equalTo("abc")));
    }

    @Test
    void route_userPrefix_isStrippedBeforeForwarding() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("abc")))
            .get().uri("/user/api/v1/bank-users/ping")
            .exchange()
            .expectStatus().isOk();

        DownstreamWireMock.SERVER.verify(0, getRequestedFor(urlPathMatching("/user/.*")));
        DownstreamWireMock.SERVER.verify(getRequestedFor(urlEqualTo("/api/v1/bank-users/ping")));
    }

    @Test
    void route_noToken_returns401AndNothingForwarded() {
        webTestClient.get().uri("/user/api/v1/bank-users/ping")
            .exchange()
            .expectStatus().isUnauthorized();

        assertThat(DownstreamWireMock.SERVER.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    void route_invalidBearerToken_returns401() {
        webTestClient.get().uri("/user/api/v1/bank-users/ping")
            .header("Authorization", "Bearer not-a-jwt")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void route_registerEndpoint_permitAllWithoutToken() {
        DownstreamWireMock.SERVER.stubFor(
            post(urlEqualTo("/api/v1/bank-users/register")).willReturn(okJson("{}")));

        webTestClient.post().uri("/user/api/v1/bank-users/register")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void route_registerEndpoint_forwardsSystemUserHeader() {
        DownstreamWireMock.SERVER.stubFor(
            post(urlEqualTo("/api/v1/bank-users/register")).willReturn(okJson("{}")));

        webTestClient.post().uri("/user/api/v1/bank-users/register")
            .exchange()
            .expectStatus().isOk();

        DownstreamWireMock.SERVER.verify(
            com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo("/api/v1/bank-users/register"))
                .withHeader("X-Auth-Id", equalTo("SYSTEM USER")));
    }

    @Test
    void route_serviceActuator_permitAllWithoutToken() {
        DownstreamWireMock.SERVER.stubFor(
            get(urlEqualTo("/actuator/health")).willReturn(okJson("{\"status\":\"UP\"}")));

        webTestClient.get().uri("/user/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void route_serviceActuator_forwardsSystemUserHeader() {
        DownstreamWireMock.SERVER.stubFor(
            get(urlEqualTo("/actuator/health")).willReturn(okJson("{\"status\":\"UP\"}")));

        webTestClient.get().uri("/user/actuator/health")
            .exchange()
            .expectStatus().isOk();

        DownstreamWireMock.SERVER.verify(
            getRequestedFor(urlEqualTo("/actuator/health")).withHeader("X-Auth-Id", equalTo("SYSTEM USER")));
    }

    @Test
    void route_downstream400_statusAndBodyPassThrough() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("abc")))
            .get().uri("/user/api/v1/errors/400")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.code").isEqualTo("400");
    }

    @Test
    void route_downstream503_statusPassesThrough() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("abc")))
            .get().uri("/banking-core/api/v1/errors/503")
            .exchange()
            .expectStatus().isEqualTo(503);
    }

    @Test
    void route_unknownPath_authenticated_returns404() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("abc")))
            .get().uri("/nope/api/v1/x")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void route_unknownPath_unauthenticated_returns401() {
        webTestClient.get().uri("/nope/api/v1/x")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void route_clientHeaders_arePropagated() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("abc")))
            .get().uri("/user/api/v1/bank-users/ping")
            .header("X-Trace-Test", "t1")
            .exchange()
            .expectStatus().isOk();

        DownstreamWireMock.SERVER.verify(
            getRequestedFor(urlEqualTo("/api/v1/bank-users/ping")).withHeader("X-Trace-Test", equalTo("t1")));
    }
}
