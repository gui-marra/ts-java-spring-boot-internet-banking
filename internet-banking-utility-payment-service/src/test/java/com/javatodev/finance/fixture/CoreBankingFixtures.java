package com.javatodev.finance.fixture;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cross-service ids come from core-banking-service's {@code V1.0.20210427174721__temp_data.sql};
 * they are the only account numbers / utility providers used in tests and WireMock mappings.
 */
public final class CoreBankingFixtures {

    public static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    public static final String ACCOUNT_NUMBER_1 = "100015003000";
    public static final String ACCOUNT_NUMBER_2 = "100015003001";
    public static final Long UTILITY_PROVIDER_VODAFONE_ID = 1L;
    public static final String UTILITY_PROVIDER_VODAFONE = "VODAFONE";
    public static final String UTILITY_ACCOUNT_VODAFONE = "8203232565";
    public static final Long UTILITY_PROVIDER_UNKNOWN_ID = 999L;
    public static final String REFERENCE_NUMBER = "REF-0001";
    public static final String CORE_TRANSACTION_ID = "00000000-0000-0000-0000-000000000001";

    private CoreBankingFixtures() {
    }

    public static UtilityPaymentRequest aUtilityPaymentRequest(String account, long providerId, long amount) {
        return aUtilityPaymentRequest(account, providerId, BigDecimal.valueOf(amount));
    }

    public static UtilityPaymentRequest aUtilityPaymentRequest(String account, long providerId, BigDecimal amount) {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount(account);
        request.setProviderId(providerId);
        request.setAmount(amount);
        request.setReferenceNumber(REFERENCE_NUMBER);
        return request;
    }

    public static UtilityPaymentRequest aUtilityPaymentRequest() {
        return aUtilityPaymentRequest(ACCOUNT_NUMBER_1, UTILITY_PROVIDER_VODAFONE_ID, 250);
    }

    public static UtilityPaymentEntity aUtilityPaymentEntity(Long id, TransactionStatus status) {
        UtilityPaymentEntity entity = new UtilityPaymentEntity();
        entity.setId(id);
        entity.setProviderId(UTILITY_PROVIDER_VODAFONE_ID);
        entity.setAmount(BigDecimal.valueOf(250));
        entity.setReferenceNumber(REFERENCE_NUMBER);
        entity.setAccount(ACCOUNT_NUMBER_1);
        entity.setStatus(status);
        if (status == TransactionStatus.SUCCESS) {
            entity.setTransactionId(CORE_TRANSACTION_ID);
        }
        entity.setCreatedBy("SYSTEM_USER");
        entity.setModifiedBy("SYSTEM_USER");
        entity.setCreatedDate(Instant.parse("2024-01-01T00:00:00Z"));
        entity.setModifiedDate(Instant.parse("2024-01-01T00:00:00Z"));
        entity.setVersion(0L);
        return entity;
    }

    public static UtilityPayment aUtilityPayment(TransactionStatus status) {
        UtilityPayment dto = new UtilityPayment();
        dto.setProviderId(UTILITY_PROVIDER_VODAFONE_ID);
        dto.setAmount(BigDecimal.valueOf(250));
        dto.setReferenceNumber(REFERENCE_NUMBER);
        dto.setAccount(ACCOUNT_NUMBER_1);
        dto.setStatus(status);
        return dto;
    }

    public static UtilityPaymentResponse aCoreUtilityPaymentResponse() {
        return UtilityPaymentResponse.builder()
            .message("Utility Payment Successfully Processed")
            .transactionId(CORE_TRANSACTION_ID)
            .build();
    }
}
