package com.javatodev.finance;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/** JVM-singleton WireMock replacing core-banking-service; mappings loaded from classpath. */
public final class CoreBankingStub {

    public static final WireMockServer SERVER = new WireMockServer(
        options().dynamicPort().usingFilesUnderClasspath("wiremock/core-banking-service"));

    static {
        SERVER.start();
    }

    private CoreBankingStub() {
    }
}
