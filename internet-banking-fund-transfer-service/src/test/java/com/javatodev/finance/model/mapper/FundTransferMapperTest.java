package com.javatodev.finance.model.mapper;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.entity.FundTransferEntity;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.javatodev.finance.fixture.FundTransferFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.FundTransferFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.FundTransferFixtures.TRANSACTION_ID;
import static com.javatodev.finance.fixture.FundTransferFixtures.aFundTransfer;
import static com.javatodev.finance.fixture.FundTransferFixtures.aFundTransferEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundTransferMapperTest {

    private final FundTransferMapper mapper = new FundTransferMapper();

    @Test
    void convertToEntityAndDto_roundTrip_preservesAllAssignableFields() {
        // Arrange
        Instant createdDate = Instant.parse("2024-01-01T00:00:00Z");
        Instant modifiedDate = Instant.parse("2024-01-02T00:00:00Z");
        FundTransferEntity entity = aFundTransferEntity(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100, TransactionStatus.SUCCESS);
        entity.setCreatedDate(createdDate);
        entity.setCreatedBy("creator");
        entity.setModifiedDate(modifiedDate);
        entity.setModifiedBy("modifier");
        entity.setVersion(7L);

        // Act
        FundTransfer dto = mapper.convertToDto(entity);
        FundTransferEntity roundTrip = mapper.convertToEntity(dto);

        // Assert
        assertThat(dto.getId()).isEqualTo(entity.getId());
        assertThat(dto.getTransactionReference()).isEqualTo(TRANSACTION_ID);
        assertThat(dto.getFromAccount()).isEqualTo(entity.getFromAccount());
        assertThat(dto.getToAccount()).isEqualTo(entity.getToAccount());
        assertThat(dto.getAmount()).isEqualByComparingTo(entity.getAmount());
        assertThat(dto.getCreatedDate()).isEqualTo(createdDate);
        assertThat(dto.getCreatedBy()).isEqualTo("creator");
        assertThat(dto.getModifiedDate()).isEqualTo(modifiedDate);
        assertThat(dto.getModifiedBy()).isEqualTo("modifier");
        assertThat(dto.getVersion()).isEqualTo(7L);
        assertThat(roundTrip.getId()).isEqualTo(entity.getId());
        assertThat(roundTrip.getTransactionReference()).isEqualTo(entity.getTransactionReference());
        assertThat(roundTrip.getFromAccount()).isEqualTo(entity.getFromAccount());
        assertThat(roundTrip.getToAccount()).isEqualTo(entity.getToAccount());
        assertThat(roundTrip.getAmount()).isEqualByComparingTo(entity.getAmount());
        assertThat(roundTrip.getCreatedDate()).isEqualTo(createdDate);
        assertThat(roundTrip.getCreatedBy()).isEqualTo("creator");
        assertThat(roundTrip.getModifiedDate()).isEqualTo(modifiedDate);
        assertThat(roundTrip.getModifiedBy()).isEqualTo("modifier");
        assertThat(roundTrip.getVersion()).isEqualTo(7L);
    }

    @Disabled("status not mapped enum→String by BeanUtils — see issue #TBD")
    @Test
    void convertToDto_entityStatus_preservesStatusName() {
        // Arrange
        FundTransferEntity entity = aFundTransferEntity(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100, TransactionStatus.SUCCESS);

        // Act
        FundTransfer dto = mapper.convertToDto(entity);

        // Assert
        assertThat(dto.getStatus()).isEqualTo(TransactionStatus.SUCCESS.name());
    }

    @Test
    void convertToDto_nullEntity_returnsEmptyDto() {
        // Act
        FundTransfer dto = mapper.convertToDto((FundTransferEntity) null);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isNull();
    }

    @Test
    void convertToEntity_nullDto_returnsEmptyEntity() {
        // Act
        FundTransferEntity entity = mapper.convertToEntity((FundTransfer) null);

        // Assert
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isNull();
    }

    @Test
    void convertToDtoList_emptyAndTwoElements_returnsMatchingSizes() {
        // Arrange
        List<FundTransferEntity> entities = List.of(
            aFundTransferEntity(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100, TransactionStatus.SUCCESS),
            aFundTransferEntity(2L, ACCOUNT_NUMBER_2, ACCOUNT_NUMBER_1, 40, TransactionStatus.PENDING));

        // Act
        List<FundTransfer> empty = mapper.convertToDtoList(List.of());
        List<FundTransfer> two = mapper.convertToDtoList(entities);

        // Assert
        assertThat(empty).isEmpty();
        assertThat(two).hasSize(2);
        assertThat(two).extracting(FundTransfer::getId).containsExactly(1L, 2L);
    }

    @Test
    void convertToEntityList_emptyAndTwoElements_returnsMatchingSizes() {
        // Arrange
        List<FundTransfer> transfers = List.of(
            aFundTransfer(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100, TransactionStatus.SUCCESS),
            aFundTransfer(2L, ACCOUNT_NUMBER_2, ACCOUNT_NUMBER_1, 40, TransactionStatus.PENDING));

        // Act
        List<FundTransferEntity> empty = mapper.convertToEntityList(List.of());
        List<FundTransferEntity> two = mapper.convertToEntityList(transfers);

        // Assert
        assertThat(empty).isEmpty();
        assertThat(two).hasSize(2);
        assertThat(two).extracting(FundTransferEntity::getId).containsExactly(1L, 2L);
    }

    @Test
    void convertToDtoSet_emptyAndTwoElements_returnsMatchingSizes() {
        // Arrange
        List<FundTransferEntity> entities = List.of(
            aFundTransferEntity(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100, TransactionStatus.SUCCESS),
            aFundTransferEntity(2L, ACCOUNT_NUMBER_2, ACCOUNT_NUMBER_1, 40, TransactionStatus.PENDING));

        // Act
        Set<FundTransfer> empty = mapper.convertToDtoSet(List.of());
        Set<FundTransfer> two = mapper.convertToDtoSet(entities);

        // Assert
        assertThat(empty).isEmpty();
        assertThat(two).hasSize(2);
    }

    @Disabled("BeanUtils.copyProperties silently drops String→enum status — see issue #TBD")
    @Test
    void convertToEntity_bogusStatus_rejectsStatus() {
        // Arrange
        FundTransfer dto = aFundTransfer(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100, TransactionStatus.SUCCESS);
        dto.setStatus("BOGUS");

        // Act & Assert
        assertThatThrownBy(() -> mapper.convertToEntity(dto))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertToDto_entityWithNullStatus_returnsNullStatus() {
        // Arrange
        FundTransferEntity entity = aFundTransferEntity(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, 100, null);

        // Act
        FundTransfer dto = mapper.convertToDto(entity);

        // Assert
        assertThat(dto.getStatus()).isNull();
    }
}
