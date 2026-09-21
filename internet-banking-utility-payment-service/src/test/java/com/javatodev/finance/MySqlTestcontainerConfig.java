package com.javatodev.finance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainerConfig {

    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>("mysql:8.4").withDatabaseName("banking_core_utility_payment_service");

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysql() {
        return MYSQL;
    }
}
