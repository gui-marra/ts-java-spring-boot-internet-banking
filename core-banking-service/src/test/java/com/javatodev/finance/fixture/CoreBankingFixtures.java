package com.javatodev.finance.fixture;

import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.entity.UtilityAccountEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public final class CoreBankingFixtures {

    public static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    public static final String ACCOUNT_NUMBER_1 = "100015003000";
    public static final String ACCOUNT_NUMBER_2 = "100015003001";
    public static final String ACCOUNT_NUMBER_LOW_BALANCE = "100015003003";
    public static final BigDecimal SEEDED_BALANCE = new BigDecimal("100000.00");
    public static final BigDecimal SEEDED_LOW_BALANCE = new BigDecimal("12000.00");
    public static final Long USER_ID_1 = 1L;
    public static final String USER_EMAIL_1 = "sam@gmail.com";
    public static final String USER_IDENTIFICATION_1 = "808829932V";
    public static final Long UTILITY_PROVIDER_VODAFONE_ID = 1L;
    public static final String UTILITY_PROVIDER_VODAFONE = "VODAFONE";
    public static final String UTILITY_ACCOUNT_VODAFONE = "8203232565";

    private CoreBankingFixtures() {
    }

    public static BankAccount aBankAccount(String number, long balance) {
        BankAccount account = new BankAccount();
        account.setId(1L);
        account.setNumber(number);
        account.setType(AccountType.SAVINGS_ACCOUNT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAvailableBalance(BigDecimal.valueOf(balance));
        account.setActualBalance(BigDecimal.valueOf(balance));

        User user = new User();
        user.setId(USER_ID_1);
        user.setFirstName("Sam");
        user.setLastName("Silva");
        user.setEmail(USER_EMAIL_1);
        user.setIdentificationNumber(USER_IDENTIFICATION_1);
        user.setBankAccounts(Collections.emptyList());
        account.setUser(user);
        return account;
    }

    public static BankAccountEntity anAccountEntity(String number, long balance) {
        BankAccountEntity account = new BankAccountEntity();
        account.setId(1L);
        account.setNumber(number);
        account.setType(AccountType.SAVINGS_ACCOUNT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAvailableBalance(BigDecimal.valueOf(balance));
        account.setActualBalance(BigDecimal.valueOf(balance));
        return account;
    }

    public static User aUser(String identificationNumber, List<BankAccount> bankAccounts) {
        User user = new User();
        user.setId(USER_ID_1);
        user.setFirstName("Sam");
        user.setLastName("Silva");
        user.setEmail(USER_EMAIL_1);
        user.setIdentificationNumber(identificationNumber);
        user.setBankAccounts(bankAccounts);
        return user;
    }

    public static UserEntity aUserEntity(String identificationNumber, List<BankAccountEntity> accounts) {
        UserEntity user = new UserEntity();
        user.setId(USER_ID_1);
        user.setFirstName("Sam");
        user.setLastName("Silva");
        user.setEmail(USER_EMAIL_1);
        user.setIdentificationNumber(identificationNumber);
        user.setAccounts(accounts);
        return user;
    }

    public static UtilityAccount aUtilityAccount(long id, String providerName) {
        UtilityAccount account = new UtilityAccount();
        account.setId(id);
        account.setNumber(UTILITY_ACCOUNT_VODAFONE);
        account.setProviderName(providerName);
        return account;
    }

    public static UtilityAccountEntity aUtilityAccountEntity(long id, String providerName) {
        UtilityAccountEntity account = new UtilityAccountEntity();
        account.setId(id);
        account.setNumber(UTILITY_ACCOUNT_VODAFONE);
        account.setProviderName(providerName);
        return account;
    }

    public static FundTransferRequest aFundTransferRequest(String from, String to, long amount) {
        return aFundTransferRequest(from, to, BigDecimal.valueOf(amount));
    }

    public static FundTransferRequest aFundTransferRequest(String from, String to, BigDecimal amount) {
        return new FundTransferRequest(from, to, amount);
    }

    public static UtilityPaymentRequest aUtilityPaymentRequest(String account, long providerId, long amount) {
        return aUtilityPaymentRequest(account, providerId, BigDecimal.valueOf(amount));
    }

    public static UtilityPaymentRequest aUtilityPaymentRequest(
        String account, long providerId, BigDecimal amount) {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount(account);
        request.setProviderId(providerId);
        request.setAmount(amount);
        request.setReferenceNumber("REF-0001");
        return request;
    }

    public static TransactionEntity aTransactionEntity(
        BankAccountEntity account, TransactionType type, long amount) {
        return TransactionEntity.builder()
            .id(1L)
            .account(account)
            .transactionType(type)
            .amount(BigDecimal.valueOf(amount))
            .referenceNumber("REF-0001")
            .transactionId("00000000-0000-0000-0000-000000000001")
            .build();
    }
}
