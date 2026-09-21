package com.javatodev.finance.repository;

import com.javatodev.finance.MySqlTestcontainerConfig;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

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
    private static final String OTHER_ID = "22222222-2222-2222-2222-222222222222";

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    private BankAccountEntity accountA;
    private BankAccountEntity accountB;

    @BeforeEach
    void persistAccounts() {
        accountA = persistAccount(ACCOUNT_A);
        accountB = persistAccount(ACCOUNT_B);
    }

    @Test
    void findByAccountNumberOrderByIdDesc_returnsOnlyThatAccountsTransactionsNewestFirst() {
        TransactionEntity first = persistTransaction(accountA, TRANSFER_ID, -100);
        persistTransaction(accountB, TRANSFER_ID, 100);
        TransactionEntity second = persistTransaction(accountA, OTHER_ID, -50);
        entityManager.flush();
        entityManager.clear();

        List<TransactionEntity> result = transactionRepository.findByAccountNumberOrderByIdDesc(ACCOUNT_A);

        assertThat(result).extracting(TransactionEntity::getId)
            .containsExactly(second.getId(), first.getId());
        assertThat(result).extracting(t -> t.getAccount().getNumber())
            .containsOnly(ACCOUNT_A);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-50));
        assertThat(result.get(1).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-100));
    }

    @Test
    void findByAccountNumberOrderByIdDesc_unknownAccount_returnsEmpty() {
        persistTransaction(accountA, TRANSFER_ID, -100);
        entityManager.flush();

        assertThat(transactionRepository.findByAccountNumberOrderByIdDesc("999999999999")).isEmpty();
    }

    @Test
    void findByTransactionId_returnsBothLegsOfTransfer() {
        TransactionEntity debit = persistTransaction(accountA, TRANSFER_ID, -100);
        TransactionEntity credit = persistTransaction(accountB, TRANSFER_ID, 100);
        persistTransaction(accountA, OTHER_ID, -50);
        entityManager.flush();
        entityManager.clear();

        List<TransactionEntity> result = transactionRepository.findByTransactionId(TRANSFER_ID);

        assertThat(result).extracting(TransactionEntity::getId)
            .containsExactlyInAnyOrder(debit.getId(), credit.getId());
        assertThat(result).extracting(TransactionEntity::getTransactionId).containsOnly(TRANSFER_ID);
        assertThat(result).extracting(t -> t.getAccount().getNumber())
            .containsExactlyInAnyOrder(ACCOUNT_A, ACCOUNT_B);
    }

    @Test
    void findByTransactionId_unknownId_returnsEmpty() {
        persistTransaction(accountA, TRANSFER_ID, -100);
        entityManager.flush();

        assertThat(transactionRepository.findByTransactionId("00000000-0000-0000-0000-000000000000")).isEmpty();
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
