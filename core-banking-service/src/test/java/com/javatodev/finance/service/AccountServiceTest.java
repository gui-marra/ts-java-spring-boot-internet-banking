package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.UtilityAccountRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_IDENTIFICATION_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_ACCOUNT_VODAFONE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUserEntity;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityAccountEntity;
import static com.javatodev.finance.fixture.CoreBankingFixtures.anAccountEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private UtilityAccountRepository utilityAccountRepository;
    @InjectMocks
    private AccountService accountService;

    @Test
    void readBankAccount_existingNumber_returnsMappedDto() {
        // Arrange
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1))
            .thenReturn(Optional.of(anAccountEntity(ACCOUNT_NUMBER_1, 200)));

        // Act
        BankAccount account = accountService.readBankAccount(ACCOUNT_NUMBER_1);

        // Assert
        assertThat(account.getId()).isEqualTo(1L);
        assertThat(account.getNumber()).isEqualTo(ACCOUNT_NUMBER_1);
        assertThat(account.getType()).isEqualTo(AccountType.SAVINGS_ACCOUNT);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getActualBalance()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(account.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(200));
        verifyNoInteractions(utilityAccountRepository);
    }

    @Test
    void readBankAccount_entityWithUser_leavesUserUnmapped() {
        // Arrange
        BankAccountEntity entity = anAccountEntity(ACCOUNT_NUMBER_1, 200);
        entity.setUser(aUserEntity(USER_IDENTIFICATION_1, Collections.singletonList(entity)));
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1)).thenReturn(Optional.of(entity));

        // Act
        BankAccount account = accountService.readBankAccount(ACCOUNT_NUMBER_1);

        // Assert
        assertThat(account.getNumber()).isEqualTo(ACCOUNT_NUMBER_1);
        assertThat(account.getUser()).isNull();
    }

    @Test
    void readBankAccount_existingNumber_queriesRepositoryWithExactNumber() {
        // Arrange
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_2))
            .thenReturn(Optional.of(anAccountEntity(ACCOUNT_NUMBER_2, 50)));

        // Act
        accountService.readBankAccount(ACCOUNT_NUMBER_2);

        // Assert
        ArgumentCaptor<String> number = ArgumentCaptor.forClass(String.class);
        verify(bankAccountRepository).findByNumber(number.capture());
        assertThat(number.getValue()).isEqualTo(ACCOUNT_NUMBER_2);
    }

    @Test
    void readBankAccount_unknownNumber_throwsEntityNotFound() {
        // Arrange
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.readBankAccount(ACCOUNT_NUMBER_1))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Test
    void readBankAccount_nullNumber_throwsEntityNotFound() {
        // Arrange
        when(bankAccountRepository.findByNumber(null)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.readBankAccount(null))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Test
    void readBankAccount_blankNumber_throwsEntityNotFound() {
        // Arrange
        when(bankAccountRepository.findByNumber("  ")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.readBankAccount("  "))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Test
    void readUtilityAccountByProvider_existingProvider_returnsMappedDto() {
        // Arrange
        when(utilityAccountRepository.findByProviderName(UTILITY_PROVIDER_VODAFONE))
            .thenReturn(Optional.of(aUtilityAccountEntity(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE)));

        // Act
        UtilityAccount account = accountService.readUtilityAccount(UTILITY_PROVIDER_VODAFONE);

        // Assert
        assertThat(account.getId()).isEqualTo(UTILITY_PROVIDER_VODAFONE_ID);
        assertThat(account.getNumber()).isEqualTo(UTILITY_ACCOUNT_VODAFONE);
        assertThat(account.getProviderName()).isEqualTo(UTILITY_PROVIDER_VODAFONE);
        verifyNoInteractions(bankAccountRepository);
    }

    @Test
    void readUtilityAccountByProvider_existingProvider_queriesRepositoryWithExactName() {
        // Arrange
        when(utilityAccountRepository.findByProviderName(UTILITY_PROVIDER_VODAFONE))
            .thenReturn(Optional.of(aUtilityAccountEntity(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE)));

        // Act
        accountService.readUtilityAccount(UTILITY_PROVIDER_VODAFONE);

        // Assert
        ArgumentCaptor<String> provider = ArgumentCaptor.forClass(String.class);
        verify(utilityAccountRepository).findByProviderName(provider.capture());
        assertThat(provider.getValue()).isEqualTo(UTILITY_PROVIDER_VODAFONE);
    }

    @Test
    void readUtilityAccountByProvider_unknownProvider_throwsEntityNotFound() {
        // Arrange
        when(utilityAccountRepository.findByProviderName("UNKNOWN")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.readUtilityAccount("UNKNOWN"))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Test
    void readUtilityAccountByProvider_nullProvider_throwsEntityNotFound() {
        // Arrange
        when(utilityAccountRepository.findByProviderName(null)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.readUtilityAccount((String) null))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Test
    void readUtilityAccountByProvider_blankProvider_throwsEntityNotFound() {
        // Arrange
        when(utilityAccountRepository.findByProviderName("")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.readUtilityAccount(""))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Test
    void readUtilityAccountById_existingId_returnsMappedDto() {
        // Arrange
        when(utilityAccountRepository.findById(UTILITY_PROVIDER_VODAFONE_ID))
            .thenReturn(Optional.of(aUtilityAccountEntity(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE)));

        // Act
        UtilityAccount account = accountService.readUtilityAccount(UTILITY_PROVIDER_VODAFONE_ID);

        // Assert
        assertThat(account.getId()).isEqualTo(UTILITY_PROVIDER_VODAFONE_ID);
        assertThat(account.getNumber()).isEqualTo(UTILITY_ACCOUNT_VODAFONE);
        assertThat(account.getProviderName()).isEqualTo(UTILITY_PROVIDER_VODAFONE);
        verifyNoInteractions(bankAccountRepository);
    }

    @Test
    void readUtilityAccountById_existingId_queriesRepositoryWithExactId() {
        // Arrange
        when(utilityAccountRepository.findById(UTILITY_PROVIDER_VODAFONE_ID))
            .thenReturn(Optional.of(aUtilityAccountEntity(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE)));

        // Act
        accountService.readUtilityAccount(UTILITY_PROVIDER_VODAFONE_ID);

        // Assert
        ArgumentCaptor<Long> id = ArgumentCaptor.forClass(Long.class);
        verify(utilityAccountRepository).findById(id.capture());
        assertThat(id.getValue()).isEqualTo(UTILITY_PROVIDER_VODAFONE_ID);
    }

    @Test
    void readUtilityAccountById_unknownId_throwsEntityNotFound() {
        // Arrange
        when(utilityAccountRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.readUtilityAccount(99L))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    @Test
    void readUtilityAccountById_nullId_throwsEntityNotFound() {
        // Arrange
        when(utilityAccountRepository.findById(null)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.readUtilityAccount((Long) null))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

}
