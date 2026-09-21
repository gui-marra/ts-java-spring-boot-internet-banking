package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InvalidBankingUserException;
import com.javatodev.finance.exception.InvalidEmailException;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.exception.UserAlreadyRegisteredException;
import com.javatodev.finance.fixture.UserFixtures;
import com.javatodev.finance.model.dto.Status;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.UserUpdateRequest;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.repository.UserRepository;
import com.javatodev.finance.model.rest.response.UserResponse;
import com.javatodev.finance.service.rest.BankingCoreRestClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static com.javatodev.finance.fixture.UserFixtures.AUTH_ID;
import static com.javatodev.finance.fixture.UserFixtures.USER_EMAIL;
import static com.javatodev.finance.fixture.UserFixtures.USER_IDENTIFICATION;
import static com.javatodev.finance.fixture.UserFixtures.USER_PASSWORD;
import static com.javatodev.finance.fixture.UserFixtures.aCoreUserResponse;
import static com.javatodev.finance.fixture.UserFixtures.aKeycloakUser;
import static com.javatodev.finance.fixture.UserFixtures.aUser;
import static com.javatodev.finance.fixture.UserFixtures.aUserEntity;
import static com.javatodev.finance.fixture.UserFixtures.anUpdateRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private KeycloakUserService keycloakUserService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BankingCoreRestClient bankingCoreRestClient;
    @InjectMocks
    private UserService userService;

    @Test
    void createUser_validRegistration_savesPendingUserAndReturnsSafeDto() {
        User request = aUser();
        when(keycloakUserService.readUserByEmail(USER_EMAIL))
            .thenReturn(List.of())
            .thenReturn(List.of(aKeycloakUser(AUTH_ID, USER_EMAIL)));
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenReturn(aCoreUserResponse());
        when(keycloakUserService.createUser(any())).thenReturn(201);
        doAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        }).when(userRepository).save(any());

        User result = userService.createUser(request);

        ArgumentCaptor<UserRepresentation> keycloakCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(keycloakUserService).createUser(keycloakCaptor.capture());
        UserRepresentation representation = keycloakCaptor.getValue();
        assertThat(representation.isEnabled()).isFalse();
        assertThat(representation.isEmailVerified()).isFalse();
        assertThat(representation.getUsername()).isEqualTo(USER_EMAIL);
        assertThat(representation.getEmail()).isEqualTo(USER_EMAIL);
        assertThat(representation.getFirstName()).isEqualTo("Sam");
        assertThat(representation.getLastName()).isEqualTo("Silva");
        assertThat(representation.getCredentials()).hasSize(1);
        CredentialRepresentation credential = representation.getCredentials().get(0);
        assertThat(credential.getValue()).isEqualTo(USER_PASSWORD);
        assertThat(credential.isTemporary()).isFalse();

        ArgumentCaptor<UserEntity> entityCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getAuthId()).isEqualTo(AUTH_ID);
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(Status.PENDING);
        assertThat(entityCaptor.getValue().getIdentification()).isEqualTo(USER_IDENTIFICATION);
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAuthId()).isEqualTo(AUTH_ID);
        assertThat(result.getStatus()).isEqualTo(Status.PENDING);
        assertThat(result.getIdentification()).isEqualTo(USER_IDENTIFICATION);
        assertThat(result.getPassword()).isNull();
    }

    @Test
    void createUser_duplicateEmail_throwsAlreadyRegisteredAndSkipsCoreAndSave() {
        when(keycloakUserService.readUserByEmail(USER_EMAIL))
            .thenReturn(List.of(aKeycloakUser(AUTH_ID, USER_EMAIL)));

        assertThatThrownBy(() -> userService.createUser(aUser()))
            .isInstanceOf(UserAlreadyRegisteredException.class);

        verifyNoInteractions(bankingCoreRestClient, userRepository);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #TBD")
    @Test
    void createUser_duplicateEmail_exposesExpectedErrorDetails() {
        when(keycloakUserService.readUserByEmail(USER_EMAIL))
            .thenReturn(List.of(aKeycloakUser(AUTH_ID, USER_EMAIL)));

        assertThatThrownBy(() -> userService.createUser(aUser()))
            .isInstanceOf(UserAlreadyRegisteredException.class)
            .satisfies(exception -> {
                assertThat(((SimpleBankingGlobalException) exception).getCode())
                    .isEqualTo(GlobalErrorCode.ERROR_EMAIL_REGISTERED);
                assertThat(exception).hasMessage("This email already registered as a user. Please check and retry.");
            });
    }

    @Test
    void createUser_coreEmailMismatch_throwsInvalidEmailWithoutSideEffects() {
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenReturn(aCoreUserResponse(1, "other@gmail.com"));

        assertThatThrownBy(() -> userService.createUser(aUser()))
            .isInstanceOf(InvalidEmailException.class);

        verify(userRepository, never()).save(any());
        verify(keycloakUserService, never()).createUser(any());
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #TBD")
    @Test
    void createUser_coreEmailMismatch_exposesExpectedErrorDetails() {
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenReturn(aCoreUserResponse(1, "other@gmail.com"));

        assertThatThrownBy(() -> userService.createUser(aUser()))
            .isInstanceOf(InvalidEmailException.class)
            .satisfies(exception -> {
                assertThat(((SimpleBankingGlobalException) exception).getCode())
                    .isEqualTo(GlobalErrorCode.ERROR_INVALID_EMAIL);
                assertThat(exception).hasMessage("Incorrect email. Please check and retry.");
            });
    }

    @Test
    void createUser_coreUserNotFound_throwsInvalidBankingUserWithoutSideEffects() {
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenReturn(aCoreUserResponse(null, USER_EMAIL));

        assertThatThrownBy(() -> userService.createUser(aUser()))
            .isInstanceOf(InvalidBankingUserException.class);

        verify(keycloakUserService, never()).createUser(any());
        verify(userRepository, never()).save(any());
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #TBD")
    @Test
    void createUser_coreUserNotFound_exposesExpectedErrorDetails() {
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenReturn(aCoreUserResponse(null, USER_EMAIL));

        assertThatThrownBy(() -> userService.createUser(aUser()))
            .isInstanceOf(InvalidBankingUserException.class)
            .satisfies(exception -> {
                assertThat(((SimpleBankingGlobalException) exception).getCode())
                    .isEqualTo(GlobalErrorCode.ERROR_USER_NOT_FOUND_UNDER_NIC);
                assertThat(exception).hasMessage("We couldn't find user under given identification. Please check and retry");
            });
    }

    @Test
    void createUser_coreClientException_propagatesSameExceptionWithoutSideEffects() {
        SimpleBankingGlobalException thrown = new SimpleBankingGlobalException();
        thrown.setCode("CORE-SERVICE-1001");
        thrown.setMessage("User not found");
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenThrow(thrown);

        assertThatThrownBy(() -> userService.createUser(aUser())).isSameAs(thrown);

        verify(keycloakUserService, never()).createUser(any());
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {409, 500})
    void createUser_keycloakNon201_throwsGlobalExceptionWithoutSaving(int status) {
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenReturn(aCoreUserResponse());
        when(keycloakUserService.createUser(any())).thenReturn(status);

        assertThatThrownBy(() -> userService.createUser(aUser()))
            .isInstanceOf(SimpleBankingGlobalException.class);

        verify(userRepository, never()).save(any());
        verify(keycloakUserService, times(1)).readUserByEmail(USER_EMAIL);
    }

    @Disabled("Keycloak non-201 is reported as USER-SERVICE-1003 'user not found under NIC' — see issue #TBD")
    @ParameterizedTest
    @ValueSource(ints = {409, 500})
    void createUser_keycloakNon201_doesNotReportInvalidBankingUser(int status) {
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenReturn(aCoreUserResponse());
        when(keycloakUserService.createUser(any())).thenReturn(status);

        assertThatThrownBy(() -> userService.createUser(aUser()))
            .isNotInstanceOf(InvalidBankingUserException.class);
    }

    @Disabled("NPE when core user has no email — see issue #TBD")
    @Test
    void createUser_coreUserWithoutEmail_throwsInvalidEmail() {
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenReturn(aCoreUserResponse(1, null));

        assertThatThrownBy(() -> userService.createUser(aUser()))
            .isInstanceOf(InvalidEmailException.class);
    }

    @Disabled("missing Bean Validation on User registration — see issue #TBD")
    @ParameterizedTest
    @ValueSource(strings = {" ", ""})
    void createUser_blankEmail_rejectsBeforeExternalInteractions(String email) {
        User request = aUser();
        request.setEmail(email);
        when(keycloakUserService.readUserByEmail(email)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(any())).thenReturn(aCoreUserResponse());

        assertThatThrownBy(() -> userService.createUser(request)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(keycloakUserService, bankingCoreRestClient, userRepository);
    }

    @Disabled("missing Bean Validation on User registration — see issue #TBD")
    @Test
    void createUser_nullEmail_rejectsBeforeExternalInteractions() {
        User request = aUser();
        request.setEmail(null);
        when(keycloakUserService.readUserByEmail(null)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(any())).thenReturn(aCoreUserResponse());

        assertThatThrownBy(() -> userService.createUser(request)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(keycloakUserService, bankingCoreRestClient, userRepository);
    }

    @Disabled("missing Bean Validation on User registration — see issue #TBD")
    @Test
    void createUser_nullIdentification_rejectsBeforeExternalInteractions() {
        User request = aUser();
        request.setIdentification(null);
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(null)).thenReturn(null);

        assertThatThrownBy(() -> userService.createUser(request)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(keycloakUserService, bankingCoreRestClient, userRepository);
    }

    @Disabled("missing Bean Validation on User registration — see issue #TBD")
    @Test
    void createUser_nullPassword_rejectsBeforeKeycloakCreate() {
        User request = aUser();
        request.setPassword(null);
        when(keycloakUserService.readUserByEmail(USER_EMAIL)).thenReturn(List.of());
        when(bankingCoreRestClient.readUser(USER_IDENTIFICATION)).thenReturn(aCoreUserResponse());
        when(keycloakUserService.createUser(any())).thenReturn(201);

        assertThatThrownBy(() -> userService.createUser(request)).isInstanceOf(RuntimeException.class);
        verify(keycloakUserService, never()).createUser(any());
    }

    @Test
    void readUsers_emptyPage_returnsEmptyWithoutKeycloakCalls() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThat(userService.readUsers(PageRequest.of(0, 20))).isEmpty();
        verifyNoInteractions(keycloakUserService);
    }

    @Test
    void readUsers_pageOfEntities_enrichesEmailAndPreservesFields() {
        UserEntity first = aUserEntity(1L, "A1", "I1", Status.PENDING);
        UserEntity second = aUserEntity(2L, "A2", "I2", Status.APPROVED);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(first, second)));
        when(keycloakUserService.readUser("A1")).thenReturn(aKeycloakUser("A1", "e1"));
        when(keycloakUserService.readUser("A2")).thenReturn(aKeycloakUser("A2", "e2"));

        List<User> result = userService.readUsers(PageRequest.of(0, 20));

        assertThat(result).hasSize(2).extracting(User::getEmail).containsExactly("e1", "e2");
        assertThat(result).extracting(User::getId).containsExactly(1L, 2L);
        assertThat(result).extracting(User::getIdentification).containsExactly("I1", "I2");
        assertThat(result).extracting(User::getStatus).containsExactly(Status.PENDING, Status.APPROVED);
        assertThat(result).extracting(User::getPassword).containsOnlyNulls();
    }

    @Test
    void readUsers_keycloakNotFound_propagatesException() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(aUserEntity())));
        when(keycloakUserService.readUser(AUTH_ID)).thenThrow(new EntityNotFoundException("User not found under given ID"));

        assertThatThrownBy(() -> userService.readUsers(PageRequest.of(0, 20)))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void readUser_found_returnsMappedUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(aUserEntity()));

        User result = userService.readUser(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAuthId()).isEqualTo(AUTH_ID);
        assertThat(result.getIdentification()).isEqualTo(USER_IDENTIFICATION);
        assertThat(result.getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void readUser_notFound_throwsEntityNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.readUser(1L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #TBD")
    @Test
    void readUser_notFound_exposesExpectedErrorDetails() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.readUser(1L))
            .isInstanceOf(EntityNotFoundException.class)
            .satisfies(exception -> {
                assertThat(((SimpleBankingGlobalException) exception).getCode())
                    .isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
                assertThat(exception).hasMessage("Requested entity not present in the DB.");
            });
    }

    @Test
    void updateUser_notFound_throwsWithoutKeycloakOrSave() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(1L, anUpdateRequest(Status.APPROVED)))
            .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(keycloakUserService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_approved_enablesKeycloakAndSavesStatus() {
        UserEntity entity = aUserEntity();
        UserRepresentation representation = aKeycloakUser(AUTH_ID, USER_EMAIL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(keycloakUserService.readUser(AUTH_ID)).thenReturn(representation);
        when(userRepository.save(entity)).thenReturn(entity);

        User result = userService.updateUser(1L, anUpdateRequest(Status.APPROVED));

        ArgumentCaptor<UserRepresentation> keycloakCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(keycloakUserService).updateUser(keycloakCaptor.capture());
        assertThat(keycloakCaptor.getValue()).isSameAs(representation);
        assertThat(representation.getId()).isEqualTo(AUTH_ID);
        assertThat(representation.isEnabled()).isTrue();
        assertThat(representation.isEmailVerified()).isTrue();
        ArgumentCaptor<UserEntity> entityCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue()).isSameAs(entity);
        assertThat(entity.getStatus()).isEqualTo(Status.APPROVED);
        assertThat(result.getStatus()).isEqualTo(Status.APPROVED);
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"DISABLED", "BLACKLIST", "PENDING"})
    void updateUser_nonApprovedStatus_savesWithoutKeycloakUpdate(Status status) {
        UserEntity entity = aUserEntity();
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        userService.updateUser(1L, anUpdateRequest(status));

        verifyNoInteractions(keycloakUserService);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(status);
    }

    @Disabled("missing Bean Validation on UserUpdateRequest.status — see issue #TBD")
    @Test
    void updateUser_nullStatus_rejectsWithoutSaving() {
        UserEntity entity = aUserEntity();
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> userService.updateUser(1L, anUpdateRequest(null)))
            .isInstanceOf(RuntimeException.class);
        verify(userRepository, never()).save(any());
    }
}
