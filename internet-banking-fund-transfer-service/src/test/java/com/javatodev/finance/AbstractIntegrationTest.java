package com.javatodev.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared base for fund-transfer integration tests with a JVM-singleton MySQL container
 * and CoreBankingStub WireMock server.
 */
@Tag("integration")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(MySqlTestcontainerConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void coreBankingUrl(DynamicPropertyRegistry registry) {
        registry.add(
            "spring.cloud.openfeign.client.config.core-banking-service.url",
            CoreBankingStub.SERVER::baseUrl
        );
    }

    @BeforeEach
    void resetCoreBankingStubs() {
        CoreBankingStub.SERVER.resetAll();
    }
}
