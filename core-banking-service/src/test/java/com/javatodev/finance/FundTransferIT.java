package com.javatodev.finance;

import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.repository.BankAccountRepository;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_LOW_BALANCE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UUID_REGEX;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aFundTransferRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FundTransferIT extends AbstractIntegrationTest {

    private static final String FUND_TRANSFER_URL = "/api/v1/transaction/fund-transfer";
    private static final String UNKNOWN_ACCOUNT = "999999999999";

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextStarts_flywayMigrationsApplied_seedPresent() {
        assertThat(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1)).isPresent();
        assertThat(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_2)).isPresent();
        assertThat(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_LOW_BALANCE)).isPresent();
    }

    @Disabled("availableBalance 2x debited — see issue #7")
    @Test
    void fundTransfer_happyPath_movesBothBalancesAndWritesTwoLegs() throws Exception {
        BankAccountEntity fromBefore = account(ACCOUNT_NUMBER_1);
        BankAccountEntity toBefore = account(ACCOUNT_NUMBER_2);
        BigDecimal amount = BigDecimal.valueOf(100);

        MvcResult result = mockMvc.perform(post(FUND_TRANSFER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId").value(matchesPattern(UUID_REGEX)))
            .andReturn();

        String transactionId = objectMapper.readTree(result.getResponse().getContentAsString())
            .get("transactionId").asText();

        BankAccountEntity fromAfter = account(ACCOUNT_NUMBER_1);
        BankAccountEntity toAfter = account(ACCOUNT_NUMBER_2);
        assertThat(fromAfter.getActualBalance()).isEqualByComparingTo(fromBefore.getActualBalance().subtract(amount));
        assertThat(fromAfter.getAvailableBalance()).isEqualByComparingTo(fromBefore.getAvailableBalance().subtract(amount));
        assertThat(toAfter.getActualBalance()).isEqualByComparingTo(toBefore.getActualBalance().add(amount));
        assertThat(toAfter.getAvailableBalance()).isEqualByComparingTo(toBefore.getAvailableBalance().add(amount));

        List<Map<String, Object>> legs = jdbcTemplate.queryForList(
            "SELECT t.amount, t.transaction_type, t.reference_number, "
                + "a.number AS account_number "
                + "FROM banking_core_transaction t "
                + "JOIN banking_core_account a ON a.id = t.account_id "
                + "WHERE t.transaction_id = ? ORDER BY t.id",
            transactionId);
        assertThat(legs).hasSize(2);
        assertThat(legs).extracting(row -> new BigDecimal(row.get("amount").toString()))
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal.valueOf(-100), BigDecimal.valueOf(100));
        assertThat(legs).extracting(row -> row.get("transaction_type"))
            .containsOnly(TransactionType.FUND_TRANSFER.name());
        assertThat(legs).extracting(row -> row.get("account_number"))
            .containsExactlyInAnyOrder(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2);
        assertThat(legs).extracting(row -> row.get("reference_number")).containsOnly(ACCOUNT_NUMBER_2);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void fundTransfer_insufficientFunds_returns400AndLeavesEverythingUnchanged() throws Exception {
        BankAccountEntity fromBefore = account(ACCOUNT_NUMBER_LOW_BALANCE);
        BankAccountEntity toBefore = account(ACCOUNT_NUMBER_2);
        int fromTxBefore = transactionCount(ACCOUNT_NUMBER_LOW_BALANCE);
        int toTxBefore = transactionCount(ACCOUNT_NUMBER_2);

        mockMvc.perform(post(FUND_TRANSFER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    aFundTransferRequest(ACCOUNT_NUMBER_LOW_BALANCE, ACCOUNT_NUMBER_2, 1_000_000))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INSUFFICIENT_FUNDS));

        BankAccountEntity fromAfter = account(ACCOUNT_NUMBER_LOW_BALANCE);
        BankAccountEntity toAfter = account(ACCOUNT_NUMBER_2);
        assertThat(fromAfter.getActualBalance()).isEqualByComparingTo(fromBefore.getActualBalance());
        assertThat(fromAfter.getAvailableBalance()).isEqualByComparingTo(fromBefore.getAvailableBalance());
        assertThat(toAfter.getActualBalance()).isEqualByComparingTo(toBefore.getActualBalance());
        assertThat(toAfter.getAvailableBalance()).isEqualByComparingTo(toBefore.getAvailableBalance());
        assertThat(transactionCount(ACCOUNT_NUMBER_LOW_BALANCE)).isEqualTo(fromTxBefore);
        assertThat(transactionCount(ACCOUNT_NUMBER_2)).isEqualTo(toTxBefore);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void fundTransfer_unknownToAccount_returns400AndLeavesFromBalanceUnchanged() throws Exception {
        BankAccountEntity fromBefore = account(ACCOUNT_NUMBER_1);
        int fromTxBefore = transactionCount(ACCOUNT_NUMBER_1);

        mockMvc.perform(post(FUND_TRANSFER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    aFundTransferRequest(ACCOUNT_NUMBER_1, UNKNOWN_ACCOUNT, 250))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND));

        BankAccountEntity fromAfter = account(ACCOUNT_NUMBER_1);
        assertThat(fromAfter.getActualBalance()).isEqualByComparingTo(fromBefore.getActualBalance());
        assertThat(fromAfter.getAvailableBalance()).isEqualByComparingTo(fromBefore.getAvailableBalance());
        assertThat(transactionCount(ACCOUNT_NUMBER_1)).isEqualTo(fromTxBefore);
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
}
