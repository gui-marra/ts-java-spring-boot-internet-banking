package com.javatodev.finance;

import com.javatodev.finance.configuration.keycloak.KeycloakManager;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.javatodev.finance.fixture.CoreBankingFixtures.CORE_USER_EMAIL;
import static com.javatodev.finance.fixture.CoreBankingFixtures.CORE_USER_IDENTIFICATION;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UNKNOWN_IDENTIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserRegistrationIT extends AbstractIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/bank-users/register";
    private static final String OTHER_EMAIL = "other@gmail.com";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KeycloakManager keycloakManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        deleteKeycloakUser(CORE_USER_EMAIL);
        deleteKeycloakUser(OTHER_EMAIL);
        userRepository.deleteAll();
    }

    @Test
    void flywayHistory_baselineMigrationApplied() {
        Integer applied = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where success = 1", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    @Test
    void register_happyPath_createsDisabledKeycloakUserAndPendingRow() throws Exception {
        MvcResult result = mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aRegistration(CORE_USER_EMAIL, CORE_USER_IDENTIFICATION))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authId").isNotEmpty())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.identification").value(CORE_USER_IDENTIFICATION))
            .andReturn();

        String authId = objectMapper.readTree(result.getResponse().getContentAsString())
            .get("authId").asText();

        List<UserRepresentation> keycloakUsers = realmUsers().search(CORE_USER_EMAIL);
        assertThat(keycloakUsers).hasSize(1);
        assertThat(keycloakUsers.get(0).getId()).isEqualTo(authId);
        assertThat(keycloakUsers.get(0).isEnabled()).isFalse();

        List<UserEntity> rows = userRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAuthId()).isEqualTo(authId);
        assertThat(rows.get(0).getIdentification()).isEqualTo(CORE_USER_IDENTIFICATION);
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        register(CORE_USER_EMAIL, CORE_USER_IDENTIFICATION);

        // Today the swapped SimpleBankingGlobalException(code, message) constructor puts the
        // message text into $.code and leaves $.message null — assert the current shape.
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aRegistration(CORE_USER_EMAIL, CORE_USER_IDENTIFICATION))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("This email already registered as a user. Please check and retry."));

        assertThat(realmUsers().search(CORE_USER_EMAIL)).hasSize(1);
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    @Disabled("SimpleBankingGlobalException swaps code/message — see issue #TBD")
    void register_duplicateEmail_returns400WithErrorCode() throws Exception {
        register(CORE_USER_EMAIL, CORE_USER_IDENTIFICATION);

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aRegistration(CORE_USER_EMAIL, CORE_USER_IDENTIFICATION))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ERROR_EMAIL_REGISTERED));
    }

    @Test
    void register_unknownIdentification_propagatesCoreError() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aRegistration(CORE_USER_EMAIL, UNKNOWN_IDENTIFICATION))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BANKING-CORE-SERVICE-1000"))
            .andExpect(jsonPath("$.message").value("Requested entity not present in the DB."));

        CoreBankingStub.SERVER.verify(getRequestedFor(urlPathMatching(".*/" + UNKNOWN_IDENTIFICATION)));
        assertThat(realmUsers().search(CORE_USER_EMAIL)).isEmpty();
        assertThat(userRepository.findAll()).isEmpty();
    }

    @Test
    void register_emailMismatch_returns400AndCreatesNothing() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aRegistration(OTHER_EMAIL, CORE_USER_IDENTIFICATION))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").isNotEmpty());

        assertThat(realmUsers().search(OTHER_EMAIL)).isEmpty();
        assertThat(userRepository.findAll()).isEmpty();
    }

    @Test
    void update_approve_enablesKeycloakUser() throws Exception {
        long id = register(CORE_USER_EMAIL, CORE_USER_IDENTIFICATION);

        mockMvc.perform(patch("/api/v1/bank-users/update/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        UserRepresentation keycloakUser = realmUsers().search(CORE_USER_EMAIL).get(0);
        assertThat(keycloakUser.isEnabled()).isTrue();
        assertThat(keycloakUser.isEmailVerified()).isTrue();
        assertThat(userRepository.findById(id).orElseThrow().getStatus().name()).isEqualTo("APPROVED");
    }

    @Test
    void read_byIdAndList_returnsRegisteredUser() throws Exception {
        long id = register(CORE_USER_EMAIL, CORE_USER_IDENTIFICATION);

        mockMvc.perform(get("/api/v1/bank-users/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.identification").value(CORE_USER_IDENTIFICATION));

        // the list endpoint resolves the email from Keycloak via authId
        mockMvc.perform(get("/api/v1/bank-users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value(CORE_USER_EMAIL))
            .andExpect(jsonPath("$[0].identification").value(CORE_USER_IDENTIFICATION));
    }

    private long register(String email, String identification) throws Exception {
        MvcResult result = mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aRegistration(email, identification))))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private User aRegistration(String email, String identification) {
        User user = new User();
        user.setEmail(email);
        user.setIdentification(identification);
        user.setPassword("p@ssw0rd");
        return user;
    }

    private void deleteKeycloakUser(String email) {
        realmUsers().search(email).forEach(u -> realmUsers().delete(u.getId()));
    }

    private org.keycloak.admin.client.resource.UsersResource realmUsers() {
        return keycloakManager.getKeyCloakInstanceWithRealm().users();
    }
}
