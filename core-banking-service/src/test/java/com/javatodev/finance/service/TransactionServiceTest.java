package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UUID_REGEX;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aBankAccount;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aFundTransferRequest;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityAccount;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPaymentRequest;
import static com.javatodev.finance.fixture.CoreBankingFixtures.anAccountEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountService accountService;
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private TransactionService transactionService;

    @Disabled("availableBalance 2x debited — see issue #7")
    @Test
    void fundTransfer_sufficientBalance_debitsSourceAndCreditsTarget() {
        // Arrange
        BankAccountEntity fromEntity = anAccountEntity(ACCOUNT_NUMBER_1, 200);
        BankAccountEntity toEntity = anAccountEntity(ACCOUNT_NUMBER_2, 50);
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));
        when(accountService.readBankAccount(ACCOUNT_NUMBER_2)).thenReturn(aBankAccount(ACCOUNT_NUMBER_2, 50));
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1)).thenReturn(Optional.of(fromEntity));
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_2)).thenReturn(Optional.of(toEntity));

        // Act
        FundTransferResponse response =
            transactionService.fundTransfer(aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100));

        // Assert
        ArgumentCaptor<BankAccountEntity> savedAccounts = ArgumentCaptor.forClass(BankAccountEntity.class);
        verify(bankAccountRepository, times(2)).save(savedAccounts.capture());
        assertThat(savedAccounts.getAllValues().get(0)).isSameAs(fromEntity);
        assertThat(fromEntity.getActualBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(fromEntity.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(savedAccounts.getAllValues().get(1)).isSameAs(toEntity);
        assertThat(toEntity.getActualBalance()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(toEntity.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(150));

        ArgumentCaptor<TransactionEntity> savedTransactions = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository, times(2)).save(savedTransactions.capture());
        assertThat(savedTransactions.getAllValues())
            .extracting(TransactionEntity::getTransactionType)
            .containsExactly(TransactionType.FUND_TRANSFER, TransactionType.FUND_TRANSFER);
        assertThat(savedTransactions.getAllValues())
            .extracting(TransactionEntity::getAmount)
            .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .containsExactly(BigDecimal.valueOf(-100), BigDecimal.valueOf(100));
        assertThat(savedTransactions.getAllValues())
            .extracting(TransactionEntity::getReferenceNumber)
            .containsExactly(ACCOUNT_NUMBER_2, ACCOUNT_NUMBER_2);
        assertThat(savedTransactions.getAllValues())
            .extracting(TransactionEntity::getAccount)
            .containsExactly(fromEntity, toEntity);
        assertThat(savedTransactions.getAllValues())
            .extracting(TransactionEntity::getTransactionId)
            .allSatisfy(id -> assertThat(id).isEqualTo(response.getTransactionId()));

        assertThat(response.getMessage()).isEqualTo("Transaction successfully completed");
        assertThat(response.getTransactionId()).matches(UUID_REGEX);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void fundTransfer_amountExceedsBalance_throwsInsufficientFunds() {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));
        when(accountService.readBankAccount(ACCOUNT_NUMBER_2)).thenReturn(aBankAccount(ACCOUNT_NUMBER_2, 50));
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 300);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.fundTransfer(request))
            .isInstanceOf(InsufficientFundsException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.INSUFFICIENT_FUNDS);

        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Disabled("availableBalance 2x debited — see issue #7")
    @Test
    void fundTransfer_amountEqualsBalance_leavesZeroBalance() {
        // Arrange
        BankAccountEntity fromEntity = anAccountEntity(ACCOUNT_NUMBER_1, 200);
        BankAccountEntity toEntity = anAccountEntity(ACCOUNT_NUMBER_2, 50);
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));
        when(accountService.readBankAccount(ACCOUNT_NUMBER_2)).thenReturn(aBankAccount(ACCOUNT_NUMBER_2, 50));
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1)).thenReturn(Optional.of(fromEntity));
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_2)).thenReturn(Optional.of(toEntity));

        // Act
        transactionService.fundTransfer(aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 200));

        // Assert
        assertThat(fromEntity.getActualBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fromEntity.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(toEntity.getActualBalance()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(toEntity.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(250));
        verify(bankAccountRepository, times(2)).save(any(BankAccountEntity.class));
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void fundTransfer_fromAccountMissing_propagatesEntityNotFound() {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenThrow(new EntityNotFoundException());
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.fundTransfer(request))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);

        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void fundTransfer_fromEntityMissingInRepository_throwsEntityNotFound() {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));
        when(accountService.readBankAccount(ACCOUNT_NUMBER_2)).thenReturn(aBankAccount(ACCOUNT_NUMBER_2, 50));
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1)).thenReturn(Optional.empty());
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.fundTransfer(request))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);

        verify(bankAccountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void fundTransfer_negativeSourceBalance_throwsInsufficientFunds() {
        // Arrange
        BankAccount from = aBankAccount(ACCOUNT_NUMBER_1, 0);
        from.setActualBalance(BigDecimal.valueOf(-10));
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(from);
        when(accountService.readBankAccount(ACCOUNT_NUMBER_2)).thenReturn(aBankAccount(ACCOUNT_NUMBER_2, 50));
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 5);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.fundTransfer(request))
            .isInstanceOf(InsufficientFundsException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.INSUFFICIENT_FUNDS);

        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Disabled("availableBalance 2x debited — see issue #7")
    @Test
    void utilPayment_sufficientBalance_debitsAccountAndRecordsPayment() {
        // Arrange
        BankAccountEntity fromEntity = anAccountEntity(ACCOUNT_NUMBER_1, 100);
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 100));
        when(accountService.readUtilityAccount(UTILITY_PROVIDER_VODAFONE_ID))
            .thenReturn(aUtilityAccount(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE));
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1)).thenReturn(Optional.of(fromEntity));
        UtilityPaymentRequest request =
            aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, 40);

        // Act
        UtilityPaymentResponse response = transactionService.utilPayment(request);

        // Assert
        assertThat(fromEntity.getActualBalance()).isEqualByComparingTo(BigDecimal.valueOf(60));
        assertThat(fromEntity.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(60));

        ArgumentCaptor<TransactionEntity> saved = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(saved.capture());
        TransactionEntity transaction = saved.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.UTILITY_PAYMENT);
        assertThat(transaction.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-40));
        assertThat(transaction.getReferenceNumber()).isEqualTo(request.getReferenceNumber());
        assertThat(transaction.getAccount()).isSameAs(fromEntity);
        assertThat(transaction.getTransactionId()).matches(UUID_REGEX);
        assertThat(transaction.getTransactionId()).isEqualTo(response.getTransactionId());

        assertThat(response.getMessage()).isEqualTo("Utility payment successfully completed");
        assertThat(response.getTransactionId()).matches(UUID_REGEX);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void utilPayment_amountExceedsBalance_throwsInsufficientFunds() {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 100));
        UtilityPaymentRequest request =
            aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, 150);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.utilPayment(request))
            .isInstanceOf(InsufficientFundsException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.INSUFFICIENT_FUNDS);

        verify(accountService, never()).readUtilityAccount(any(Long.class));
        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void utilPayment_providerMissing_propagatesEntityNotFound() {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 100));
        when(accountService.readUtilityAccount(UTILITY_PROVIDER_VODAFONE_ID))
            .thenThrow(new EntityNotFoundException());
        UtilityPaymentRequest request =
            aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, 40);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.utilPayment(request))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);

        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Disabled("availableBalance 2x debited — see issue #7")
    @Test
    void internalFundTransfer_validAccounts_movesFundsAndReturnsTransactionId() {
        // Arrange
        BankAccountEntity fromEntity = anAccountEntity(ACCOUNT_NUMBER_1, 200);
        BankAccountEntity toEntity = anAccountEntity(ACCOUNT_NUMBER_2, 50);
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1)).thenReturn(Optional.of(fromEntity));
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_2)).thenReturn(Optional.of(toEntity));

        // Act
        String transactionId = transactionService.internalFundTransfer(
            aBankAccount(ACCOUNT_NUMBER_1, 200), aBankAccount(ACCOUNT_NUMBER_2, 50), BigDecimal.valueOf(100));

        // Assert
        assertThat(transactionId).matches(UUID_REGEX);
        assertThat(fromEntity.getActualBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(fromEntity.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(toEntity.getActualBalance()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(toEntity.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(150));

        ArgumentCaptor<TransactionEntity> saved = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
            .extracting(TransactionEntity::getTransactionId)
            .allSatisfy(id -> assertThat(id).isEqualTo(transactionId));
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void internalFundTransfer_toEntityMissing_throwsEntityNotFound() {
        // Arrange
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1))
            .thenReturn(Optional.of(anAccountEntity(ACCOUNT_NUMBER_1, 200)));
        when(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_2)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> transactionService.internalFundTransfer(
            aBankAccount(ACCOUNT_NUMBER_1, 200), aBankAccount(ACCOUNT_NUMBER_2, 50), BigDecimal.valueOf(100)))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting("code").isEqualTo(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);

        verify(bankAccountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Disabled("Accepts non-positive amounts — see issue #11")
    @Test
    void fundTransfer_zeroAmount_rejectsRequest() {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));
        when(accountService.readBankAccount(ACCOUNT_NUMBER_2)).thenReturn(aBankAccount(ACCOUNT_NUMBER_2, 50));
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 0);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.fundTransfer(request))
            .isInstanceOf(SimpleBankingGlobalException.class);

        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Disabled("Accepts non-positive amounts — see issue #11")
    @Test
    void fundTransfer_negativeAmount_rejectsRequest() {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 200));
        when(accountService.readBankAccount(ACCOUNT_NUMBER_2)).thenReturn(aBankAccount(ACCOUNT_NUMBER_2, 50));
        FundTransferRequest request = aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, -50);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.fundTransfer(request))
            .isInstanceOf(SimpleBankingGlobalException.class);

        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Disabled("Accepts non-positive amounts — see issue #11")
    @Test
    void utilPayment_zeroAmount_rejectsRequest() {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 100));
        UtilityPaymentRequest request = aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, 0);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.utilPayment(request))
            .isInstanceOf(SimpleBankingGlobalException.class);

        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Disabled("Accepts non-positive amounts — see issue #11")
    @Test
    void utilPayment_negativeAmount_rejectsRequest() {
        // Arrange
        when(accountService.readBankAccount(ACCOUNT_NUMBER_1)).thenReturn(aBankAccount(ACCOUNT_NUMBER_1, 100));
        UtilityPaymentRequest request = aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, -40);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.utilPayment(request))
            .isInstanceOf(SimpleBankingGlobalException.class);

        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

}
