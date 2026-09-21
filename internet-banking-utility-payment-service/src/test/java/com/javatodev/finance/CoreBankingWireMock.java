package com.javatodev.finance;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

public final class CoreBankingWireMock {

    public static final WireMockServer SERVER = new WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("wiremock/core-banking-service"));

    static {
        SERVER.start();
    }

    private CoreBankingWireMock() {
    }
}
