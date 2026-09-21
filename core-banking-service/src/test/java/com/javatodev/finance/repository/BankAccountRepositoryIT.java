package com.javatodev.finance.repository;

import com.javatodev.finance.MySqlTestcontainerConfig;
import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.UserEntity;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.SEEDED_BALANCE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_EMAIL_1;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestcontainerConfig.class)
@Tag("integration")
class BankAccountRepositoryIT {

    private static final String NEW_ACCOUNT_NUMBER = "900000000010";

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Test
    void findByNumber_persistedAccount_returnsAllFields() {
        // Arrange
        UserEntity user = entityManager.find(UserEntity.class, 1L);
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber(NEW_ACCOUNT_NUMBER);
        account.setType(AccountType.SAVINGS_ACCOUNT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setActualBalance(BigDecimal.valueOf(750));
        account.setAvailableBalance(BigDecimal.valueOf(700));
        account.setUser(user);
        entityManager.persist(account);
        entityManager.flush();
        entityManager.clear();

        // Act
        BankAccountEntity result = bankAccountRepository.findByNumber(NEW_ACCOUNT_NUMBER).orElseThrow();

        // Assert
        assertThat(result.getNumber()).isEqualTo(NEW_ACCOUNT_NUMBER);
        assertThat(result.getType()).isEqualTo(AccountType.SAVINGS_ACCOUNT);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.getActualBalance()).isEqualByComparingTo("750.00");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("700.00");
        assertThat(result.getUser().getEmail()).isEqualTo(USER_EMAIL_1);
    }

    @Test
    void findByNumber_seededAccount_returnsExpectedAccount() {
        // Arrange

        // Act
        BankAccountEntity result = bankAccountRepository.findByNumber(ACCOUNT_NUMBER_1).orElseThrow();

        // Assert
        assertThat(result.getNumber()).isEqualTo(ACCOUNT_NUMBER_1);
        assertThat(result.getActualBalance()).isEqualByComparingTo(SEEDED_BALANCE);
        assertThat(result.getAvailableBalance()).isEqualByComparingTo(SEEDED_BALANCE);
        assertThat(result.getUser().getEmail()).isEqualTo(USER_EMAIL_1);
    }

    @Test
    void findByNumber_unknownNumber_returnsEmpty() {
        // Arrange

        // Act
        var result = bankAccountRepository.findByNumber("999999999999");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findByNumber_nullNumber_returnsEmpty() {
        // Arrange

        // Act
        var result = bankAccountRepository.findByNumber(null);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findByNumber_caseOrWhitespaceDifference_returnsEmpty() {
        // Arrange

        // Act
        var result = bankAccountRepository.findByNumber(" 100015003000");

        // Assert
        assertThat(result).isEmpty();
    }
}
