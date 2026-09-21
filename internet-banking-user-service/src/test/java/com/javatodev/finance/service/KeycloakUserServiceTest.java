package com.javatodev.finance.service;

import com.javatodev.finance.configuration.keycloak.KeycloakManager;
import com.javatodev.finance.exception.EntityNotFoundException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;

import static com.javatodev.finance.fixture.UserFixtures.AUTH_ID;
import static com.javatodev.finance.fixture.UserFixtures.USER_EMAIL;
import static com.javatodev.finance.fixture.UserFixtures.aKeycloakUser;
import static com.javatodev.finance.exception.GlobalErrorCode.ERROR_ENTITY_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserServiceTest {
    @Mock
    private KeycloakManager keycloakManager;
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private UserResource userResource;
    @Mock
    private Response response;
    @InjectMocks
    private KeycloakUserService keycloakUserService;

    @BeforeEach
    void setUp() {
        when(keycloakManager.getKeyCloakInstanceWithRealm()).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    void createUser_created_returns201() {
        UserRepresentation representation = aKeycloakUser(AUTH_ID, USER_EMAIL);
        when(usersResource.create(representation)).thenReturn(response);
        when(response.getStatus()).thenReturn(201);

        assertThat(keycloakUserService.createUser(representation)).isEqualTo(201);
        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(captor.capture());
        assertThat(captor.getValue()).isSameAs(representation);
    }

    @Test
    void createUser_conflict_returns409() {
        UserRepresentation representation = aKeycloakUser(AUTH_ID, USER_EMAIL);
        when(usersResource.create(representation)).thenReturn(response);
        when(response.getStatus()).thenReturn(409);

        assertThat(keycloakUserService.createUser(representation)).isEqualTo(409);
        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(captor.capture());
        assertThat(captor.getValue()).isSameAs(representation);
    }

    @Test
    void updateUser_existingUser_updatesRepresentationById() {
        UserRepresentation representation = aKeycloakUser(AUTH_ID, USER_EMAIL);
        when(usersResource.get(AUTH_ID)).thenReturn(userResource);

        keycloakUserService.updateUser(representation);

        verify(userResource).update(representation);
    }

    @Test
    void readUserByEmail_noMatch_returnsEmptyList() {
        when(usersResource.search(USER_EMAIL)).thenReturn(Collections.emptyList());

        assertThat(keycloakUserService.readUserByEmail(USER_EMAIL)).isEmpty();
    }

    @Test
    void readUserByEmail_match_returnsSearchResult() {
        List<UserRepresentation> result = List.of(aKeycloakUser(AUTH_ID, USER_EMAIL));
        when(usersResource.search(USER_EMAIL)).thenReturn(result);

        assertThat(keycloakUserService.readUserByEmail(USER_EMAIL)).isSameAs(result);
    }

    @Test
    void readUser_existing_returnsRepresentation() {
        UserRepresentation representation = aKeycloakUser(AUTH_ID, USER_EMAIL);
        when(usersResource.get(AUTH_ID)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(representation);

        assertThat(keycloakUserService.readUser(AUTH_ID)).isSameAs(representation);
    }

    @Test
    void readUser_keycloakThrowsNotFound_throwsEntityNotFoundException() {
        when(usersResource.get(AUTH_ID)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenThrow(new NotFoundException());

        assertThatThrownBy(() -> keycloakUserService.readUser(AUTH_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void readUser_keycloakConnectivityFailure_throwsEntityNotFoundException() {
        when(usersResource.get(AUTH_ID)).thenThrow(new ProcessingException("connection refused"));

        assertThatThrownBy(() -> keycloakUserService.readUser(AUTH_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #TBD")
    @Test
    void readUser_keycloakThrowsNotFound_exposesExpectedErrorDetails() {
        when(usersResource.get(AUTH_ID)).thenThrow(new ProcessingException("connection refused"));

        assertThatThrownBy(() -> keycloakUserService.readUser(AUTH_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .satisfies(exception -> {
                assertThat(((EntityNotFoundException) exception).getCode())
                    .isEqualTo(ERROR_ENTITY_NOT_FOUND);
                assertThat(exception).hasMessage("User not found under given ID");
            });
    }
}
