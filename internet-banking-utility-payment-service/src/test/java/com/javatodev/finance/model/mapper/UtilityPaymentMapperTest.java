package com.javatodev.finance.model.mapper;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPayment;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityPaymentEntity;
import static org.assertj.core.api.Assertions.assertThat;

class UtilityPaymentMapperTest {

    private final UtilityPaymentMapper mapper = new UtilityPaymentMapper();

    @Test
    void convertToDto_entity_copiesPaymentAndAuditFields() {
        // Arrange
        UtilityPaymentEntity entity = aUtilityPaymentEntity(7L, TransactionStatus.SUCCESS);

        // Act
        UtilityPayment dto = mapper.convertToDto(entity);

        // Assert
        assertThat(dto.getProviderId()).isEqualTo(entity.getProviderId());
        assertThat(dto.getAmount()).isEqualByComparingTo(entity.getAmount());
        assertThat(dto.getReferenceNumber()).isEqualTo(entity.getReferenceNumber());
        assertThat(dto.getAccount()).isEqualTo(entity.getAccount());
        assertThat(dto.getStatus()).isEqualTo(entity.getStatus());
        assertThat(dto.getCreatedBy()).isEqualTo(entity.getCreatedBy());
        assertThat(dto.getCreatedDate()).isEqualTo(entity.getCreatedDate());
        assertThat(dto.getModifiedBy()).isEqualTo(entity.getModifiedBy());
        assertThat(dto.getModifiedDate()).isEqualTo(entity.getModifiedDate());
        assertThat(dto.getVersion()).isEqualTo(entity.getVersion());
    }

    @Test
    void convertToEntity_dto_copiesPaymentAndAuditFields() {
        // Arrange
        UtilityPayment dto = aUtilityPayment(TransactionStatus.SUCCESS);
        dto.setCreatedBy("CREATOR");
        dto.setCreatedDate(Instant.parse("2024-02-02T00:00:00Z"));
        dto.setModifiedBy("EDITOR");
        dto.setModifiedDate(Instant.parse("2024-02-03T00:00:00Z"));
        dto.setVersion(4L);

        // Act
        UtilityPaymentEntity entity = mapper.convertToEntity(dto);

        // Assert
        assertThat(entity.getProviderId()).isEqualTo(dto.getProviderId());
        assertThat(entity.getAmount()).isEqualByComparingTo(dto.getAmount());
        assertThat(entity.getReferenceNumber()).isEqualTo(dto.getReferenceNumber());
        assertThat(entity.getAccount()).isEqualTo(dto.getAccount());
        assertThat(entity.getStatus()).isEqualTo(dto.getStatus());
        assertThat(entity.getCreatedBy()).isEqualTo(dto.getCreatedBy());
        assertThat(entity.getCreatedDate()).isEqualTo(dto.getCreatedDate());
        assertThat(entity.getModifiedBy()).isEqualTo(dto.getModifiedBy());
        assertThat(entity.getModifiedDate()).isEqualTo(dto.getModifiedDate());
        assertThat(entity.getVersion()).isEqualTo(dto.getVersion());
    }

    @Test
    void convertToEntity_afterDtoRoundTrip_dropsEntityOnlyIdAndTransactionId() {
        // Arrange
        UtilityPaymentEntity entity = aUtilityPaymentEntity(7L, TransactionStatus.SUCCESS);

        // Act
        UtilityPaymentEntity roundTrip = mapper.convertToEntity(mapper.convertToDto(entity));

        // Assert
        assertThat(roundTrip.getId()).isNull();
        assertThat(roundTrip.getTransactionId()).isNull();
        assertThat(roundTrip.getProviderId()).isEqualTo(entity.getProviderId());
        assertThat(roundTrip.getAmount()).isEqualByComparingTo(entity.getAmount());
        assertThat(roundTrip.getReferenceNumber()).isEqualTo(entity.getReferenceNumber());
        assertThat(roundTrip.getAccount()).isEqualTo(entity.getAccount());
        assertThat(roundTrip.getStatus()).isEqualTo(entity.getStatus());
        assertThat(roundTrip.getCreatedBy()).isEqualTo(entity.getCreatedBy());
        assertThat(roundTrip.getCreatedDate()).isEqualTo(entity.getCreatedDate());
        assertThat(roundTrip.getModifiedBy()).isEqualTo(entity.getModifiedBy());
        assertThat(roundTrip.getModifiedDate()).isEqualTo(entity.getModifiedDate());
        assertThat(roundTrip.getVersion()).isEqualTo(entity.getVersion());
    }

    @Test
    void convertToDto_null_returnsEmptyDto() {
        // Act
        UtilityPayment dto = mapper.convertToDto((UtilityPaymentEntity) null);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.getProviderId()).isNull();
        assertThat(dto.getAmount()).isNull();
        assertThat(dto.getReferenceNumber()).isNull();
        assertThat(dto.getAccount()).isNull();
        assertThat(dto.getStatus()).isNull();
        assertThat(dto.getCreatedBy()).isNull();
        assertThat(dto.getCreatedDate()).isNull();
        assertThat(dto.getModifiedBy()).isNull();
        assertThat(dto.getModifiedDate()).isNull();
        assertThat(dto.getVersion()).isZero();
    }

    @Test
    void convertToEntity_null_returnsEmptyEntity() {
        // Act
        UtilityPaymentEntity entity = mapper.convertToEntity((UtilityPayment) null);

        // Assert
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getProviderId()).isNull();
        assertThat(entity.getAmount()).isNull();
        assertThat(entity.getTransactionId()).isNull();
        assertThat(entity.getVersion()).isZero();
    }

    @Test
    void convertToDtoList_emptyCollection_returnsEmptyList() {
        // Act
        List<UtilityPayment> dtos = mapper.convertToDtoList(List.of());

        // Assert
        assertThat(dtos).isEmpty();
    }

    @Test
    void convertToDtoList_threeEntities_preservesOrderAndSize() {
        // Arrange
        List<UtilityPaymentEntity> entities = List.of(
            aUtilityPaymentEntity(1L, TransactionStatus.PROCESSING),
            aUtilityPaymentEntity(2L, TransactionStatus.SUCCESS),
            aUtilityPaymentEntity(3L, TransactionStatus.PROCESSING));

        // Act
        List<UtilityPayment> dtos = mapper.convertToDtoList(entities);

        // Assert
        assertThat(dtos).hasSize(3);
        assertThat(dtos).extracting(UtilityPayment::getStatus)
            .containsExactly(TransactionStatus.PROCESSING, TransactionStatus.SUCCESS, TransactionStatus.PROCESSING);
    }

    @Test
    void convertToDtoSet_twoIdenticalEntities_returnsActualDistinctDtoCount() {
        // Arrange
        List<UtilityPaymentEntity> entities = List.of(
            aUtilityPaymentEntity(1L, TransactionStatus.SUCCESS),
            aUtilityPaymentEntity(2L, TransactionStatus.SUCCESS));

        // Act
        Set<UtilityPayment> dtos = mapper.convertToDtoSet(entities);

        // Assert
        assertThat(dtos).hasSize(1);
    }
}
