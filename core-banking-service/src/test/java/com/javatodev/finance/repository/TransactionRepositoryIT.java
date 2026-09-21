package com.javatodev.finance.repository;

import com.javatodev.finance.MySqlTestcontainerConfig;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;

import static com.javatodev.finance.fixture.CoreBankingFixtures.anAccountEntity;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestcontainerConfig.class)
@Tag("integration")
class TransactionRepositoryIT {

    private static final String ACCOUNT_A = "900000000001";
    private static final String ACCOUNT_B = "900000000002";
    private static final String TRANSFER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BankAccountEntity accountA;
    private BankAccountEntity accountB;

    @BeforeEach
    void persistAccounts() {
        accountA = persistAccount(ACCOUNT_A);
        accountB = persistAccount(ACCOUNT_B);
    }

    @Test
    void save_newTransaction_writesRowWithAccountFkAndAmount() {
        TransactionEntity saved = transactionRepository.save(TransactionEntity.builder()
            .account(accountA)
            .transactionType(TransactionType.FUND_TRANSFER)
            .transactionId(TRANSFER_ID)
            .referenceNumber(accountA.getNumber())
            .amount(BigDecimal.valueOf(-100))
            .build());
        entityManager.flush();

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT amount, transaction_type, reference_number, transaction_id, account_id "
                + "FROM banking_core_transaction WHERE id = ?",
            saved.getId());

        assertThat(new BigDecimal(row.get("amount").toString())).isEqualByComparingTo("-100");
        assertThat(row.get("transaction_type")).isEqualTo(TransactionType.FUND_TRANSFER.name());
        assertThat(row.get("reference_number")).isEqualTo(accountA.getNumber());
        assertThat(row.get("transaction_id")).isEqualTo(TRANSFER_ID);
        assertThat(((Number) row.get("account_id")).longValue()).isEqualTo(accountA.getId());
    }

    @Test
    void save_negativeAmountLeg_persistsScaleTwo() {
        TransactionEntity saved = transactionRepository.save(TransactionEntity.builder()
            .account(accountA)
            .transactionType(TransactionType.FUND_TRANSFER)
            .transactionId(TRANSFER_ID)
            .referenceNumber(accountA.getNumber())
            .amount(new BigDecimal("-100.5"))
            .build());
        entityManager.flush();

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT amount FROM banking_core_transaction WHERE id = ?",
            saved.getId());

        assertThat(new BigDecimal(row.get("amount").toString())).isEqualByComparingTo("-100.50");
    }

    @Test
    void count_afterTwoLegs_returnsTwo() {
        persistTransaction(accountA, TRANSFER_ID, -100);
        persistTransaction(accountB, TRANSFER_ID, 100);
        entityManager.flush();

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Disabled("TransactionEntity has no no-arg constructor, Hibernate cannot load rows — see issue #9")
    @Test
    void findById_existingRow_returnsEntity() {
        TransactionEntity saved = persistTransaction(accountA, TRANSFER_ID, -100);
        entityManager.flush();
        entityManager.clear();

        assertThat(transactionRepository.findById(saved.getId()))
            .isPresent()
            .get()
            .extracting(TransactionEntity::getTransactionId)
            .isEqualTo(TRANSFER_ID);
    }

    private BankAccountEntity persistAccount(String number) {
        BankAccountEntity account = anAccountEntity(number, 1_000);
        account.setId(null);
        return entityManager.persist(account);
    }

    private TransactionEntity persistTransaction(BankAccountEntity account, String transactionId, long amount) {
        return entityManager.persist(TransactionEntity.builder()
            .account(account)
            .transactionType(TransactionType.FUND_TRANSFER)
            .transactionId(transactionId)
            .referenceNumber(account.getNumber())
            .amount(BigDecimal.valueOf(amount))
            .build());
    }
}
