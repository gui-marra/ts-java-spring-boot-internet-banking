package com.javatodev.finance.service;

import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;
import com.javatodev.finance.repository.UtilityPaymentRepository;
import com.javatodev.finance.service.rest.BankingCoreRestClient;

import feign.FeignException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.CORE_TRANSACTION_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.REFERENCE_NUMBER;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_UNKNOWN_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aCoreFeignException;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aCoreUtilityPaymentResponse;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPaymentEntity;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPaymentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilityPaymentServiceTest {

    @Mock
    private UtilityPaymentRepository utilityPaymentRepository;
    @Mock
    private BankingCoreRestClient bankingCoreRestClient;

    private UtilityPaymentService utilityPaymentService;

    @BeforeEach
    void setUp() {
        utilityPaymentService = new UtilityPaymentService(utilityPaymentRepository, bankingCoreRestClient);
    }

    @Test
    void utilPayment_validRequest_savesProcessingThenSuccessAndReturnsResponse() {
        // Arrange
        UtilityPaymentRequest request = aUtilityPaymentRequest();
        AtomicReference<UtilityPaymentEntity> processingSave = new AtomicReference<>();
        when(utilityPaymentRepository.save(any())).thenAnswer(invocation -> {
            UtilityPaymentEntity entity = invocation.getArgument(0);
            if (processingSave.get() == null) {
                processingSave.set(copyEntity(entity));
            }
            return entity;
        });
        when(bankingCoreRestClient.utilityPayment(request)).thenReturn(aCoreUtilityPaymentResponse());

        // Act
        UtilityPaymentResponse response = utilityPaymentService.utilPayment(request);

        // Assert
        ArgumentCaptor<UtilityPaymentEntity> saved = ArgumentCaptor.forClass(UtilityPaymentEntity.class);
        verify(utilityPaymentRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).hasSize(2);
        assertThat(processingSave.get().getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(processingSave.get().getTransactionId()).isNull();
        assertThat(processingSave.get().getProviderId()).isEqualTo(request.getProviderId());
        assertThat(processingSave.get().getAmount()).isEqualByComparingTo(request.getAmount());
        assertThat(processingSave.get().getReferenceNumber()).isEqualTo(request.getReferenceNumber());
        assertThat(processingSave.get().getAccount()).isEqualTo(request.getAccount());
        assertThat(saved.getAllValues().get(1).getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(saved.getAllValues().get(1).getTransactionId()).isEqualTo(CORE_TRANSACTION_ID);

        ArgumentCaptor<UtilityPaymentRequest> coreRequest = ArgumentCaptor.forClass(UtilityPaymentRequest.class);
        verify(bankingCoreRestClient).utilityPayment(coreRequest.capture());
        assertThat(coreRequest.getValue()).isEqualTo(request);
        assertThat(response.getMessage()).isEqualTo("Utility Payment Successfully Processed");
        assertThat(response.getTransactionId()).isEqualTo(CORE_TRANSACTION_ID);
    }

    /**
     * The module has no FAILED transition today; the row intentionally stays PROCESSING
     * (see the utility-payment row in docs/testing/integration-tests.md).
     */
    @ParameterizedTest
    @MethodSource("coreFailures")
    void utilPayment_coreFailure_propagatesAndLeavesProcessingRow(Throwable failure) {
        // Arrange
        UtilityPaymentRequest request = aUtilityPaymentRequest();
        when(utilityPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bankingCoreRestClient.utilityPayment(request)).thenThrow(failure);

        // Act & Assert
        assertThatThrownBy(() -> utilityPaymentService.utilPayment(request))
            .isInstanceOf(failure.getClass());

        ArgumentCaptor<UtilityPaymentEntity> saved = ArgumentCaptor.forClass(UtilityPaymentEntity.class);
        verify(utilityPaymentRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(saved.getAllValues()).noneMatch(entity -> entity.getStatus() == TransactionStatus.SUCCESS);
    }

    private static Stream<Arguments> coreFailures() {
        return Stream.of(
            Arguments.of(aCoreFeignException()),
            Arguments.of(new RuntimeException("core unavailable")));
    }

    @Disabled("Non-positive/null amount is accepted by the service — see issue #TBD")
    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void utilPayment_invalidAmount_rejectsRequest(BigDecimal amount) {
        // Arrange
        UtilityPaymentRequest request = aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, amount);

        // Act & Assert
        assertThatThrownBy(() -> utilityPaymentService.utilPayment(request))
            .isInstanceOf(SimpleBankingGlobalException.class);
        verifyNoInteractions(bankingCoreRestClient);
        verify(utilityPaymentRepository, never()).save(any());
    }

    private static Stream<BigDecimal> invalidAmounts() {
        return Stream.of(BigDecimal.ZERO, BigDecimal.valueOf(-1), null);
    }

    @Disabled("Blank/null referenceNumber is accepted by the service — see issue #TBD")
    @ParameterizedTest
    @MethodSource("invalidReferenceNumbers")
    void utilPayment_invalidReferenceNumber_rejectsRequest(String referenceNumber) {
        // Arrange
        UtilityPaymentRequest request = aUtilityPaymentRequest();
        request.setReferenceNumber(referenceNumber);

        // Act & Assert
        assertThatThrownBy(() -> utilityPaymentService.utilPayment(request))
            .isInstanceOf(SimpleBankingGlobalException.class);
        verifyNoInteractions(bankingCoreRestClient);
        verify(utilityPaymentRepository, never()).save(any());
    }

    private static Stream<String> invalidReferenceNumbers() {
        return Stream.of(" ", null);
    }

    @Disabled("Blank/null account is accepted by the service — see issue #TBD")
    @ParameterizedTest
    @MethodSource("invalidAccounts")
    void utilPayment_invalidAccount_rejectsRequest(String account) {
        // Arrange
        UtilityPaymentRequest request = aUtilityPaymentRequest();
        request.setAccount(account);

        // Act & Assert
        assertThatThrownBy(() -> utilityPaymentService.utilPayment(request))
            .isInstanceOf(SimpleBankingGlobalException.class);
        verifyNoInteractions(bankingCoreRestClient);
        verify(utilityPaymentRepository, never()).save(any());
    }

    private static Stream<String> invalidAccounts() {
        return Stream.of(" ", null);
    }

    @Disabled("Null providerId is accepted by the service — see issue #TBD")
    @Test
    void utilPayment_nullProviderId_rejectsRequest() {
        // Arrange
        UtilityPaymentRequest request = aUtilityPaymentRequest();
        request.setProviderId(null);

        // Act & Assert
        assertThatThrownBy(() -> utilityPaymentService.utilPayment(request))
            .isInstanceOf(SimpleBankingGlobalException.class);
        verifyNoInteractions(bankingCoreRestClient);
        verify(utilityPaymentRepository, never()).save(any());
    }

    @Test
    void utilPayment_hugeAmount_forwardsAndSavesAmountUnchanged() {
        // Arrange
        BigDecimal amount = new BigDecimal("99999999999999999999.99");
        UtilityPaymentRequest request = aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, amount);
        when(utilityPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bankingCoreRestClient.utilityPayment(request)).thenReturn(aCoreUtilityPaymentResponse());

        // Act
        utilityPaymentService.utilPayment(request);

        // Assert
        ArgumentCaptor<UtilityPaymentRequest> coreRequest = ArgumentCaptor.forClass(UtilityPaymentRequest.class);
        verify(bankingCoreRestClient).utilityPayment(coreRequest.capture());
        assertThat(coreRequest.getValue().getAmount()).isEqualByComparingTo(amount);
        ArgumentCaptor<UtilityPaymentEntity> saved = ArgumentCaptor.forClass(UtilityPaymentEntity.class);
        verify(utilityPaymentRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(entity ->
            assertThat(entity.getAmount()).isEqualByComparingTo(amount));
    }

    @Test
    void utilPayment_unknownProvider_forwardsProviderUnchanged() {
        // Arrange
        UtilityPaymentRequest request = aUtilityPaymentRequest();
        request.setProviderId(UTILITY_PROVIDER_UNKNOWN_ID);
        when(utilityPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bankingCoreRestClient.utilityPayment(request)).thenReturn(aCoreUtilityPaymentResponse());

        // Act
        utilityPaymentService.utilPayment(request);

        // Assert
        ArgumentCaptor<UtilityPaymentRequest> coreRequest = ArgumentCaptor.forClass(UtilityPaymentRequest.class);
        verify(bankingCoreRestClient).utilityPayment(coreRequest.capture());
        assertThat(coreRequest.getValue().getProviderId()).isEqualTo(UTILITY_PROVIDER_UNKNOWN_ID);
    }

    @Test
    void utilPayment_unknownProviderCoreFailure_propagatesAndSavesProcessingOnly() {
        // Arrange
        UtilityPaymentRequest request = aUtilityPaymentRequest();
        request.setProviderId(UTILITY_PROVIDER_UNKNOWN_ID);
        when(utilityPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bankingCoreRestClient.utilityPayment(request)).thenThrow(aCoreFeignException());

        // Act & Assert
        assertThatThrownBy(() -> utilityPaymentService.utilPayment(request))
            .isInstanceOf(FeignException.class);

        ArgumentCaptor<UtilityPaymentEntity> saved = ArgumentCaptor.forClass(UtilityPaymentEntity.class);
        verify(utilityPaymentRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        verify(bankingCoreRestClient).utilityPayment(request);
    }

    @Test
    void readPayments_pageWithEntities_returnsMappedDtosAndForwardsPageable() {
        // Arrange
        Pageable pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "amount"));
        Page<UtilityPaymentEntity> page = new PageImpl<>(List.of(
            aUtilityPaymentEntity(1L, TransactionStatus.SUCCESS),
            aUtilityPaymentEntity(2L, TransactionStatus.PROCESSING)), pageable, 2);
        when(utilityPaymentRepository.findAll(pageable)).thenReturn(page);

        // Act
        List<UtilityPayment> payments = utilityPaymentService.readPayments(pageable);

        // Assert
        ArgumentCaptor<Pageable> requestedPageable = ArgumentCaptor.forClass(Pageable.class);
        verify(utilityPaymentRepository).findAll(requestedPageable.capture());
        assertThat(requestedPageable.getValue()).isEqualTo(pageable);
        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(payments.get(0).getProviderId()).isEqualTo(UTILITY_PROVIDER_VODAFONE_ID);
        assertThat(payments.get(0).getReferenceNumber()).isEqualTo(REFERENCE_NUMBER);
        assertThat(payments.get(0).getAccount()).isEqualTo(ACCOUNT_NUMBER_1);
        assertThat(payments.get(1).getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(payments.get(1).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(payments.get(1).getProviderId()).isEqualTo(UTILITY_PROVIDER_VODAFONE_ID);
        assertThat(payments.get(1).getReferenceNumber()).isEqualTo(REFERENCE_NUMBER);
        assertThat(payments.get(1).getAccount()).isEqualTo(ACCOUNT_NUMBER_1);
    }

    @Test
    void readPayments_emptyPage_returnsEmptyList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 5);
        when(utilityPaymentRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        // Act
        List<UtilityPayment> payments = utilityPaymentService.readPayments(pageable);

        // Assert
        assertThat(payments).isEmpty();
    }

    @Test
    void readPayments_duplicateEntities_returnsBothDtos() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 5);
        UtilityPaymentEntity first = aUtilityPaymentEntity(1L, TransactionStatus.SUCCESS);
        UtilityPaymentEntity second = aUtilityPaymentEntity(1L, TransactionStatus.SUCCESS);
        when(utilityPaymentRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));

        // Act
        List<UtilityPayment> payments = utilityPaymentService.readPayments(pageable);

        // Assert
        assertThat(payments).hasSize(2);
    }

    private static UtilityPaymentEntity copyEntity(UtilityPaymentEntity source) {
        UtilityPaymentEntity copy = new UtilityPaymentEntity();
        copy.setId(source.getId());
        copy.setProviderId(source.getProviderId());
        copy.setAmount(source.getAmount());
        copy.setReferenceNumber(source.getReferenceNumber());
        copy.setAccount(source.getAccount());
        copy.setTransactionId(source.getTransactionId());
        copy.setStatus(source.getStatus());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedDate(source.getCreatedDate());
        copy.setModifiedBy(source.getModifiedBy());
        copy.setModifiedDate(source.getModifiedDate());
        copy.setVersion(source.getVersion());
        return copy;
    }
}
