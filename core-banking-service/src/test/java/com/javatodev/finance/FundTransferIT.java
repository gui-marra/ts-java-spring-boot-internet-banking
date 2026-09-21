package com.javatodev.finance;

import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

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
    private TransactionRepository transactionRepository;

    @Test
    void contextStarts_flywayMigrationsApplied_seedPresent() {
        assertThat(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1)).isPresent();
        assertThat(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_2)).isPresent();
        assertThat(bankAccountRepository.findByNumber(ACCOUNT_NUMBER_LOW_BALANCE)).isPresent();
    }

    @Disabled("availableBalance 2x (#7); TransactionEntity not loadable (#9)")
    @Test
    void fundTransfer_happyPath_movesBothBalancesAndWritesTwoLegs() throws Exception {
        BankAccountEntity fromBefore = account(ACCOUNT_NUMBER_1);
        BankAccountEntity toBefore = account(ACCOUNT_NUMBER_2);
        BigDecimal amount = BigDecimal.valueOf(250);

        MvcResult result = mockMvc.perform(post(FUND_TRANSFER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    aFundTransferRequest(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 250))))
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

        List<TransactionEntity> legs = transactionRepository.findByTransactionId(transactionId);
        assertThat(legs).hasSize(2);
        assertThat(legs).extracting(TransactionEntity::getTransactionType).containsOnly(TransactionType.FUND_TRANSFER);
        assertThat(legs).extracting(TransactionEntity::getReferenceNumber).containsOnly(ACCOUNT_NUMBER_2);
        assertThat(legs).extracting(TransactionEntity::getAmount)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactlyInAnyOrder(amount.negate(), amount);
        assertThat(legs).extracting(t -> t.getAccount().getNumber())
            .containsExactlyInAnyOrder(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2);
        TransactionEntity debit = legs.stream().filter(t -> t.getAmount().signum() < 0).findFirst().orElseThrow();
        assertThat(debit.getAccount().getNumber()).isEqualTo(ACCOUNT_NUMBER_1);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void fundTransfer_insufficientFunds_returns400AndLeavesEverythingUnchanged() throws Exception {
        BankAccountEntity fromBefore = account(ACCOUNT_NUMBER_LOW_BALANCE);
        BankAccountEntity toBefore = account(ACCOUNT_NUMBER_2);
        int fromTxBefore = transactionRepository.findByAccountNumberOrderByIdDesc(ACCOUNT_NUMBER_LOW_BALANCE).size();
        int toTxBefore = transactionRepository.findByAccountNumberOrderByIdDesc(ACCOUNT_NUMBER_2).size();

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
        assertThat(transactionRepository.findByAccountNumberOrderByIdDesc(ACCOUNT_NUMBER_LOW_BALANCE)).hasSize(fromTxBefore);
        assertThat(transactionRepository.findByAccountNumberOrderByIdDesc(ACCOUNT_NUMBER_2)).hasSize(toTxBefore);
    }

    @Disabled("code/message swapped in SimpleBankingGlobalException — see issue #8")
    @Test
    void fundTransfer_unknownToAccount_returns400AndLeavesFromBalanceUnchanged() throws Exception {
        BankAccountEntity fromBefore = account(ACCOUNT_NUMBER_1);
        int fromTxBefore = transactionRepository.findByAccountNumberOrderByIdDesc(ACCOUNT_NUMBER_1).size();

        mockMvc.perform(post(FUND_TRANSFER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    aFundTransferRequest(ACCOUNT_NUMBER_1, UNKNOWN_ACCOUNT, 250))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ERROR_ENTITY_NOT_FOUND));

        BankAccountEntity fromAfter = account(ACCOUNT_NUMBER_1);
        assertThat(fromAfter.getActualBalance()).isEqualByComparingTo(fromBefore.getActualBalance());
        assertThat(fromAfter.getAvailableBalance()).isEqualByComparingTo(fromBefore.getAvailableBalance());
        assertThat(transactionRepository.findByAccountNumberOrderByIdDesc(ACCOUNT_NUMBER_1)).hasSize(fromTxBefore);
    }

    private BankAccountEntity account(String number) {
        return bankAccountRepository.findByNumber(number).orElseThrow();
    }
}
