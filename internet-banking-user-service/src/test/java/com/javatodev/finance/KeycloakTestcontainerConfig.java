package com.javatodev.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.testcontainers.containers.wait.strategy.Wait;

import dasniko.testcontainers.keycloak.KeycloakContainer;

/**
 * JVM-singleton Keycloak with the compose realm imported
 * ({@code keycloak/realm-export.json} is copied onto the test classpath from
 * {@code docker-compose/keycloak} by {@code processTestResources}). One
 * container per JVM so every cached Spring context sees the same URL.
 */
public final class KeycloakTestcontainerConfig {

    public static final String REALM = "javatodev-internet-banking";
    public static final String ADMIN_CLIENT_ID = "javatodev-internet-banking-kc-api-client";

    public static final KeycloakContainer KEYCLOAK =
        new KeycloakContainer("quay.io/keycloak/keycloak:23.0.7")
            .withRealmImportFile("keycloak/realm-export.json")
            .withEnv("KC_HEALTH_ENABLED", "true")
            // Keycloak 23 serves /health on the main port, not the 9000 management
            // port the container default waits on.
            .waitingFor(Wait.forHttp("/health/started").forPort(8080).forStatusCode(200));

    static {
        KEYCLOAK.start();
    }

    private KeycloakTestcontainerConfig() {
    }

    /** Reads the service-account client secret out of the realm export so it is never hard-coded. */
    public static String adminClientSecret() {
        try (InputStream in = KeycloakTestcontainerConfig.class
                .getClassLoader().getResourceAsStream("keycloak/realm-export.json")) {
            JsonNode realm = new ObjectMapper().readTree(in);
            for (JsonNode client : realm.get("clients")) {
                if (ADMIN_CLIENT_ID.equals(client.get("clientId").asText())) {
                    return client.get("secret").asText();
                }
            }
            throw new IllegalStateException(ADMIN_CLIENT_ID + " not found in realm-export.json");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
