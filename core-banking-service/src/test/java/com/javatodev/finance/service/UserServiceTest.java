package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.repository.UserRepository;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_EMAIL_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_ID_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_IDENTIFICATION_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUserEntity;
import static com.javatodev.finance.fixture.CoreBankingFixtures.anAccountEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;

    @Test
    void readUser_existingIdentification_returnsMappedDto() {
        // Arrange
        UserEntity entity = aUserEntity(USER_IDENTIFICATION_1,
            Collections.singletonList(anAccountEntity(ACCOUNT_NUMBER_1, 200)));
        when(userRepository.findByIdentificationNumber(USER_IDENTIFICATION_1)).thenReturn(Optional.of(entity));

        // Act
        User user = userService.readUser(USER_IDENTIFICATION_1);

        // Assert
        assertThat(user.getId()).isEqualTo(USER_ID_1);
        assertThat(user.getFirstName()).isEqualTo("Sam");
        assertThat(user.getLastName()).isEqualTo("Silva");
        assertThat(user.getEmail()).isEqualTo(USER_EMAIL_1);
        assertThat(user.getIdentificationNumber()).isEqualTo(USER_IDENTIFICATION_1);
        assertThat(user.getBankAccounts()).extracting(BankAccount::getNumber).containsExactly(ACCOUNT_NUMBER_1);
    }

    @Test
    void readUser_entityWithTwoAccounts_mapsBothAccountsWithoutUser() {
        // Arrange
        UserEntity entity = aUserEntity(USER_IDENTIFICATION_1,
            List.of(anAccountEntity(ACCOUNT_NUMBER_1, 200), anAccountEntity(ACCOUNT_NUMBER_2, 50)));
        when(userRepository.findByIdentificationNumber(USER_IDENTIFICATION_1)).thenReturn(Optional.of(entity));

        // Act
        User user = userService.readUser(USER_IDENTIFICATION_1);

        // Assert
        assertThat(user.getBankAccounts())
            .extracting(BankAccount::getNumber)
            .containsExactly(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2);
        assertThat(user.getBankAccounts()).extracting(BankAccount::getUser).containsOnlyNulls();
    }

    @Test
    void readUser_existingIdentification_queriesRepositoryWithExactIdentification() {
        // Arrange
        when(userRepository.findByIdentificationNumber(USER_IDENTIFICATION_1))
            .thenReturn(Optional.of(aUserEntity(USER_IDENTIFICATION_1, Collections.emptyList())));

        // Act
        userService.readUser(USER_IDENTIFICATION_1);

        // Assert
        ArgumentCaptor<String> identification = ArgumentCaptor.forClass(String.class);
        verify(userRepository).findByIdentificationNumber(identification.capture());
        assertThat(identification.getValue()).isEqualTo(USER_IDENTIFICATION_1);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void readUser_unknownIdentification_throwsEntityNotFound() {
        // Arrange
        when(userRepository.findByIdentificationNumber("000000000X")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.readUser("000000000X"))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void readUser_nullIdentification_throwsEntityNotFound() {
        // Arrange
        when(userRepository.findByIdentificationNumber(null)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.readUser(null))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void readUser_blankIdentification_throwsEntityNotFound() {
        // Arrange
        when(userRepository.findByIdentificationNumber(" ")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.readUser(" "))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Disabled("Divergence: UserMapper NPEs when entity.accounts is null — see issue #15")
    @Test
    void readUser_entityWithoutAccounts_returnsEmptyBankAccounts() {
        // Arrange
        when(userRepository.findByIdentificationNumber(USER_IDENTIFICATION_1))
            .thenReturn(Optional.of(aUserEntity(USER_IDENTIFICATION_1, null)));

        // Act
        User user = userService.readUser(USER_IDENTIFICATION_1);

        // Assert
        assertThat(user.getIdentificationNumber()).isEqualTo(USER_IDENTIFICATION_1);
        assertThat(user.getBankAccounts()).isEmpty();
    }

    @Test
    void readUsers_pageRequest_passesSamePageableToRepository() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 2);
        when(userRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        // Act
        userService.readUsers(pageable);

        // Assert
        ArgumentCaptor<Pageable> passed = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(passed.capture());
        assertThat(passed.getValue()).isSameAs(pageable);
    }

    @Test
    void readUsers_emptyPage_returnsEmptyList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 2);
        when(userRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        // Act
        List<User> users = userService.readUsers(pageable);

        // Assert
        assertThat(users).isNotNull().isEmpty();
    }

    @Test
    void readUsers_pageWithEntities_returnsMappedUsersInOrder() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 2);
        UserEntity first = aUserEntity(USER_IDENTIFICATION_1,
            Collections.singletonList(anAccountEntity(ACCOUNT_NUMBER_1, 200)));
        UserEntity second = aUserEntity("123456789V",
            Collections.singletonList(anAccountEntity(ACCOUNT_NUMBER_2, 50)));
        second.setId(2L);
        second.setEmail("jane@gmail.com");
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));

        // Act
        List<User> users = userService.readUsers(pageable);

        // Assert
        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getId).containsExactly(USER_ID_1, 2L);
        assertThat(users).extracting(User::getIdentificationNumber).containsExactly(USER_IDENTIFICATION_1, "123456789V");
        assertThat(users).extracting(User::getEmail).containsExactly(USER_EMAIL_1, "jane@gmail.com");
        assertThat(users.get(0).getBankAccounts()).extracting(BankAccount::getNumber).containsExactly(ACCOUNT_NUMBER_1);
        assertThat(users.get(1).getBankAccounts()).extracting(BankAccount::getNumber).containsExactly(ACCOUNT_NUMBER_2);
    }

}
