package com.javatodev.finance.model.mapper;

import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.entity.UtilityAccountEntity;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_ACCOUNT_VODAFONE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE_ID;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityAccount;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUtilityAccountEntity;
import static org.assertj.core.api.Assertions.assertThat;

class UtilityAccountMapperTest {

    private final UtilityAccountMapper mapper = new UtilityAccountMapper();

    @Test
    void convertToDto_entity_copiesAllFields() {
        // Arrange
        UtilityAccountEntity entity = aUtilityAccountEntity(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE);

        // Act
        UtilityAccount dto = mapper.convertToDto(entity);

        // Assert
        assertThat(dto.getId()).isEqualTo(UTILITY_PROVIDER_VODAFONE_ID);
        assertThat(dto.getNumber()).isEqualTo(UTILITY_ACCOUNT_VODAFONE);
        assertThat(dto.getProviderName()).isEqualTo(UTILITY_PROVIDER_VODAFONE);
    }

    @Test
    void convertToEntity_afterConvertToDto_roundTripsEveryField() {
        // Arrange
        UtilityAccountEntity original = aUtilityAccountEntity(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE);

        // Act
        UtilityAccountEntity roundTripped = mapper.convertToEntity(mapper.convertToDto(original));

        // Assert
        assertThat(roundTripped).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void convertToDto_nullEntity_returnsEmptyDto() {
        // Act
        UtilityAccount dto = mapper.convertToDto((UtilityAccountEntity) null);

        // Assert
        assertThat(dto).isNotNull().hasAllNullFieldsOrProperties();
    }

    @Test
    void convertToEntity_nullDto_returnsEmptyEntity() {
        // Act
        UtilityAccountEntity entity = mapper.convertToEntity((UtilityAccount) null);

        // Assert
        assertThat(entity).isNotNull().hasAllNullFieldsOrProperties();
    }

    @Test
    void convertToDtoList_twoEntities_mapsBothInOrder() {
        // Arrange
        List<UtilityAccountEntity> entities = List.of(
            aUtilityAccountEntity(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE),
            aUtilityAccountEntity(2L, "DIALOG"));

        // Act
        List<UtilityAccount> dtos = mapper.convertToDtoList(entities);

        // Assert
        assertThat(dtos).extracting(UtilityAccount::getProviderName).containsExactly(UTILITY_PROVIDER_VODAFONE, "DIALOG");
    }

    @Test
    void convertToEntityList_twoDtos_mapsBothInOrder() {
        // Arrange
        List<UtilityAccount> dtos = List.of(
            aUtilityAccount(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE),
            aUtilityAccount(2L, "DIALOG"));

        // Act
        List<UtilityAccountEntity> entities = mapper.convertToEntityList(dtos);

        // Assert
        assertThat(entities).extracting(UtilityAccountEntity::getId).containsExactly(UTILITY_PROVIDER_VODAFONE_ID, 2L);
    }

    @Test
    void convertToDtoSet_twoEntities_mapsBoth() {
        // Arrange
        List<UtilityAccountEntity> entities = List.of(
            aUtilityAccountEntity(UTILITY_PROVIDER_VODAFONE_ID, UTILITY_PROVIDER_VODAFONE),
            aUtilityAccountEntity(2L, "DIALOG"));

        // Act
        Set<UtilityAccount> dtos = mapper.convertToDtoSet(entities);

        // Assert
        assertThat(dtos).extracting(UtilityAccount::getId).containsExactlyInAnyOrder(UTILITY_PROVIDER_VODAFONE_ID, 2L);
    }

    @Test
    void collectionHelpers_emptyInput_returnEmptyCollections() {
        // Act & Assert
        assertThat(mapper.convertToDtoList(Collections.emptyList())).isEmpty();
        assertThat(mapper.convertToEntityList(Collections.emptyList())).isEmpty();
        assertThat(mapper.convertToDtoSet(Collections.emptyList())).isEmpty();
    }

}
