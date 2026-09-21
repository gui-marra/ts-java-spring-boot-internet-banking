package com.javatodev.finance.fixture;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.entity.FundTransferEntity;

import java.math.BigDecimal;

public final class FundTransferFixtures {

    public static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    public static final String ACCOUNT_NUMBER_1 = "100015003000";
    public static final String ACCOUNT_NUMBER_2 = "100015003001";
    public static final String AUTH_ID = "auth-user-0001";
    public static final String TRANSACTION_ID = "00000000-0000-0000-0000-000000000001";
    public static final String TRANSACTION_ID_2 = "00000000-0000-0000-0000-000000000002";

    private FundTransferFixtures() {
    }

    public static FundTransferRequest aFundTransferRequest(String from, String to, long amount) {
        return aFundTransferRequest(from, to, BigDecimal.valueOf(amount));
    }

    public static FundTransferRequest aFundTransferRequest(String from, String to, BigDecimal amount) {
        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount(from);
        request.setToAccount(to);
        request.setAmount(amount);
        request.setAuthID(AUTH_ID);
        return request;
    }

    public static FundTransferEntity aFundTransferEntity(
        Long id, String from, String to, long amount, TransactionStatus status) {
        FundTransferEntity entity = new FundTransferEntity();
        entity.setId(id);
        entity.setFromAccount(from);
        entity.setToAccount(to);
        entity.setAmount(BigDecimal.valueOf(amount));
        entity.setStatus(status);
        if (status == TransactionStatus.SUCCESS) {
            entity.setTransactionReference(TRANSACTION_ID);
        }
        return entity;
    }

    public static FundTransfer aFundTransfer(
        Long id, String from, String to, long amount, TransactionStatus status) {
        FundTransfer transfer = new FundTransfer();
        transfer.setId(id);
        transfer.setFromAccount(from);
        transfer.setToAccount(to);
        transfer.setAmount(BigDecimal.valueOf(amount));
        transfer.setStatus(status.name());
        if (status == TransactionStatus.SUCCESS) {
            transfer.setTransactionReference(TRANSACTION_ID);
        }
        return transfer;
    }

    public static FundTransferResponse aFundTransferResponse(String transactionId) {
        FundTransferResponse response = new FundTransferResponse();
        response.setMessage("Transaction successfully completed");
        response.setTransactionId(transactionId);
        return response;
    }
}
