package com.javatodev.finance.model.repository;

import com.javatodev.finance.MySqlTestcontainerConfig;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.entity.FundTransferEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static com.javatodev.finance.fixture.FundTransferFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.FundTransferFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.FundTransferFixtures.TRANSACTION_ID;
import static com.javatodev.finance.fixture.FundTransferFixtures.aFundTransferEntity;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestcontainerConfig.class)
@Tag("integration")
class FundTransferRepositoryIT {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FundTransferRepository fundTransferRepository;

    @BeforeEach
    void cleanTable() {
        fundTransferRepository.deleteAll();
    }

    @Test
    void save_pendingEntity_generatesIdAndRoundTripsEveryColumn() {
        // Arrange
        FundTransferEntity entity = aPendingEntity(new BigDecimal("100.50"));

        // Act
        FundTransferEntity saved = fundTransferRepository.save(entity);
        entityManager.flush();
        entityManager.clear();
        FundTransferEntity found = fundTransferRepository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(found.getFromAccount()).isEqualTo(ACCOUNT_NUMBER_1);
        assertThat(found.getToAccount()).isEqualTo(ACCOUNT_NUMBER_2);
        assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(found.getAmount().scale()).isEqualTo(2);
        assertThat(found.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(found.getTransactionReference()).isNull();
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void save_statusUpdatedToSuccessWithReference_incrementsVersion() {
        // Arrange
        FundTransferEntity saved = fundTransferRepository.save(aPendingEntity(BigDecimal.valueOf(100)));
        entityManager.flush();

        // Act
        saved.setStatus(TransactionStatus.SUCCESS);
        saved.setTransactionReference(TRANSACTION_ID);
        fundTransferRepository.save(saved);
        entityManager.flush();
        entityManager.clear();
        FundTransferEntity found = fundTransferRepository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(found.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(found.getTransactionReference()).isEqualTo(TRANSACTION_ID);
        assertThat(found.getVersion()).isEqualTo(1L);
    }

    @Test
    void findAll_pageOfTwoSortedByIdDesc_returnsHighestIdsFirstWithTotalThree() {
        // Arrange
        FundTransferEntity first = fundTransferRepository.save(aPendingEntity(BigDecimal.valueOf(10)));
        FundTransferEntity second = fundTransferRepository.save(aPendingEntity(BigDecimal.valueOf(20)));
        FundTransferEntity third = fundTransferRepository.save(aPendingEntity(BigDecimal.valueOf(30)));
        entityManager.flush();
        entityManager.clear();

        // Act
        Page<FundTransferEntity> page = fundTransferRepository.findAll(
            PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "id")));

        // Assert
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(FundTransferEntity::getId)
            .containsExactly(third.getId(), second.getId());
        assertThat(page.getContent()).extracting(FundTransferEntity::getId).doesNotContain(first.getId());
    }

    @Test
    void save_amountWithThreeDecimals_isStoredWithTwoDecimalScale() {
        // Arrange
        FundTransferEntity saved = fundTransferRepository.save(aPendingEntity(new BigDecimal("100.005")));
        entityManager.flush();
        entityManager.clear();

        // Act
        FundTransferEntity found = fundTransferRepository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(found.getAmount().scale()).isEqualTo(2);
        assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("100.01"));
    }

    @Test
    void save_nullStatusAndNullAmount_isAcceptedByNullableColumns() {
        // Arrange
        FundTransferEntity entity = aPendingEntity(null);
        entity.setStatus(null);

        // Act
        FundTransferEntity saved = fundTransferRepository.save(entity);
        entityManager.flush();
        entityManager.clear();
        FundTransferEntity found = fundTransferRepository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(found.getStatus()).isNull();
        assertThat(found.getAmount()).isNull();
    }

    private FundTransferEntity aPendingEntity(BigDecimal amount) {
        FundTransferEntity entity = aFundTransferEntity(null, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 0, TransactionStatus.PENDING);
        entity.setAmount(amount);
        return entity;
    }
}
