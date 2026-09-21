package com.javatodev.finance.repository;

import com.javatodev.finance.MySqlTestcontainerConfig;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.REFERENCE_NUMBER;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPaymentEntity;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DataJpaTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestcontainerConfig.class)
class UtilityPaymentRepositoryIT {

    @Autowired
    private UtilityPaymentRepository repo;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void findAllSortedByAmountDescending() {
        UtilityPaymentEntity lower = entityManager.persist(aUtilityPaymentEntity(null, TransactionStatus.PENDING));
        lower.setAmount(new BigDecimal("10.00"));
        UtilityPaymentEntity higher = entityManager.persist(aUtilityPaymentEntity(null, TransactionStatus.SUCCESS));
        higher.setAmount(new BigDecimal("20.00"));
        entityManager.flush();

        List<UtilityPaymentEntity> rows = repo.findAll(
            PageRequest.of(0, 10, Sort.by("amount").descending())).getContent();

        assertThat(rows).extracting(UtilityPaymentEntity::getAmount)
            .containsExactly(new BigDecimal("20.00"), new BigDecimal("10.00"));
    }

    @Test
    void transactionStatusesRoundTrip() {
        List<Long> ids = Arrays.stream(TransactionStatus.values())
            .map(status -> {
                UtilityPaymentEntity entity = entityManager.persist(
                    aUtilityPaymentEntity(null, status));
                entityManager.flush();
                return entity;
            })
            .map(UtilityPaymentEntity::getId)
            .toList();
        entityManager.clear();

        assertThat(ids).hasSize(TransactionStatus.values().length);
        for (int i = 0; i < ids.size(); i++) {
            assertThat(entityManager.find(UtilityPaymentEntity.class, ids.get(i)).getStatus())
                .isEqualTo(TransactionStatus.values()[i]);
        }
    }

    @Test
    void amountRoundTrips() {
        UtilityPaymentEntity entity = aUtilityPaymentEntity(null, TransactionStatus.PENDING);
        entity.setAmount(new BigDecimal("1234.56"));
        UtilityPaymentEntity saved = repo.saveAndFlush(entity);
        entityManager.clear();

        assertThat(entityManager.find(UtilityPaymentEntity.class, saved.getId()).getAmount())
            .isEqualByComparingTo("1234.56");
    }

    @Test
    void auditColumnsPopulatedOnPersist() {
        UtilityPaymentEntity entity = new UtilityPaymentEntity();
        entity.setProviderId(UTILITY_PROVIDER_VODAFONE_ID);
        entity.setAmount(new BigDecimal("250.00"));
        entity.setReferenceNumber(REFERENCE_NUMBER);
        entity.setAccount(ACCOUNT_NUMBER_1);
        entity.setStatus(TransactionStatus.PENDING);

        UtilityPaymentEntity saved = repo.saveAndFlush(entity);

        assertThat(saved.getCreatedBy()).isEqualTo("SYSTEM_USER");
        assertThat(saved.getCreatedDate()).isNotNull();
    }

    @Test
    void versionStartsAtZeroAndIncrementsOnUpdate() {
        UtilityPaymentEntity entity = repo.saveAndFlush(
            aUtilityPaymentEntity(null, TransactionStatus.PENDING));
        assertThat(entity.getVersion()).isZero();

        entity.setStatus(TransactionStatus.SUCCESS);
        UtilityPaymentEntity updated = repo.saveAndFlush(entity);

        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void countIsZeroOnEmptyTable() {
        assertThat(repo.count()).isZero();
    }
}
