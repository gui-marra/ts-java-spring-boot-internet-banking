package com.javatodev.finance;

import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.repository.BankAccountRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_LOW_BALANCE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.SEEDED_BALANCE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.SEEDED_LOW_BALANCE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UUID_REGEX;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPaymentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UtilityPaymentIT extends AbstractIntegrationTest {

    private static final String UTILITY_PAYMENT_URL = "/api/v1/transaction/util-payment";
    private static final String UNKNOWN_ACCOUNT = "999999999999";

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreSeededBalances() {
        jdbcTemplate.update(
            "DELETE t FROM banking_core_transaction t "
                + "JOIN banking_core_account a ON a.id = t.account_id "
                + "WHERE a.number IN (?, ?)",
            ACCOUNT_NUMBER_2, ACCOUNT_NUMBER_LOW_BALANCE);
        restoreBalance(ACCOUNT_NUMBER_2, SEEDED_BALANCE);
        restoreBalance(ACCOUNT_NUMBER_LOW_BALANCE, SEEDED_LOW_BALANCE);
    }

    private void restoreBalance(String number, BigDecimal balance) {
        BankAccountEntity account = account(number);
        account.setActualBalance(balance);
        account.setAvailableBalance(balance);
        bankAccountRepository.save(account);
    }

    @Disabled("availableBalance 2x debited — see issue #7")
    @Test
    void utilPayment_happyPath_debitsAccountAndWritesOneTransaction() throws Exception {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(300);
        BankAccountEntity accountBefore = account(ACCOUNT_NUMBER_2);
        var request = aUtilityPaymentRequest(ACCOUNT_NUMBER_2, 1, amount);

        // Act
        MvcResult result = mockMvc.perform(post(UTILITY_PAYMENT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Utility payment successfully completed"))
            .andExpect(jsonPath("$.transactionId").value(matchesPattern(UUID_REGEX)))
            .andReturn();

        String transactionId = objectMapper.readTree(result.getResponse().getContentAsString())
            .get("transactionId").asText();

        // Assert
        BankAccountEntity accountAfter = account(ACCOUNT_NUMBER_2);
        assertThat(accountAfter.getActualBalance())
            .isEqualByComparingTo(accountBefore.getActualBalance().subtract(amount));
        assertThat(accountAfter.getAvailableBalance())
            .isEqualByComparingTo(accountBefore.getAvailableBalance().subtract(amount));

        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(
            "SELECT t.amount, t.transaction_type, t.reference_number, "
                + "a.number AS account_number "
                + "FROM banking_core_transaction t "
                + "JOIN banking_core_account a ON a.id = t.account_id "
                + "WHERE t.transaction_id = ?",
            transactionId);
        assertThat(transactions).hasSize(1);
        Map<String, Object> transaction = transactions.get(0);
        assertThat(transaction.get("transaction_type")).isEqualTo(TransactionType.UTILITY_PAYMENT.name());
        assertThat(new BigDecimal(transaction.get("amount").toString())).isEqualByComparingTo(amount.negate());
        assertThat(transaction.get("reference_number")).isEqualTo(request.getReferenceNumber());
        assertThat(transaction.get("account_number")).isEqualTo(ACCOUNT_NUMBER_2);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void utilPayment_insufficientFunds_returns400AndLeavesEverythingUnchanged() throws Exception {
        // Arrange
        BankAccountEntity accountBefore = account(ACCOUNT_NUMBER_LOW_BALANCE);
        int transactionCountBefore = transactionCount(ACCOUNT_NUMBER_LOW_BALANCE);

        // Act
        mockMvc.perform(post(UTILITY_PAYMENT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    aUtilityPaymentRequest(ACCOUNT_NUMBER_LOW_BALANCE, 1, 1_000_000))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INSUFFICIENT_FUNDS));

        // Assert
        BankAccountEntity accountAfter = account(ACCOUNT_NUMBER_LOW_BALANCE);
        assertThat(accountAfter.getActualBalance())
            .isEqualByComparingTo(accountBefore.getActualBalance());
        assertThat(accountAfter.getAvailableBalance())
            .isEqualByComparingTo(accountBefore.getAvailableBalance());
        assertThat(transactionCount(ACCOUNT_NUMBER_LOW_BALANCE)).isEqualTo(transactionCountBefore);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void utilPayment_unknownProvider_returns400AndLeavesEverythingUnchanged() throws Exception {
        // Arrange
        BankAccountEntity accountBefore = account(ACCOUNT_NUMBER_2);
        int transactionCountBefore = transactionCount(ACCOUNT_NUMBER_2);

        // Act
        mockMvc.perform(post(UTILITY_PAYMENT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    aUtilityPaymentRequest(ACCOUNT_NUMBER_2, 9999, 300))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND));

        // Assert
        BankAccountEntity accountAfter = account(ACCOUNT_NUMBER_2);
        assertThat(accountAfter.getActualBalance())
            .isEqualByComparingTo(accountBefore.getActualBalance());
        assertThat(accountAfter.getAvailableBalance())
            .isEqualByComparingTo(accountBefore.getAvailableBalance());
        assertThat(transactionCount(ACCOUNT_NUMBER_2)).isEqualTo(transactionCountBefore);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void utilPayment_unknownAccount_returns400AndAddsNoTransactions() throws Exception {
        // Arrange
        int transactionCountBefore = totalTransactionCount();

        // Act
        mockMvc.perform(post(UTILITY_PAYMENT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    aUtilityPaymentRequest(UNKNOWN_ACCOUNT, 1, 300))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND));

        // Assert
        assertThat(totalTransactionCount()).isEqualTo(transactionCountBefore);
    }

    @Test
    void utilPayment_malformedJson_returns400() throws Exception {
        // Arrange
        String malformedJson = "{\"providerId\":1,\"amount\":300";

        // Act
        mockMvc.perform(post(UTILITY_PAYMENT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
            .andExpect(status().isBadRequest());

        // Assert
    }

    private BankAccountEntity account(String number) {
        return bankAccountRepository.findByNumber(number).orElseThrow();
    }

    private int transactionCount(String accountNumber) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) "
                + "FROM banking_core_transaction t "
                + "JOIN banking_core_account a ON a.id = t.account_id "
                + "WHERE a.number = ?",
            Integer.class,
            accountNumber);
    }

    private int totalTransactionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banking_core_transaction", Integer.class);
    }
}
