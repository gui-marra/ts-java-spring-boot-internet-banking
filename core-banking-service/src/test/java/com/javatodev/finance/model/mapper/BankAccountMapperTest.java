package com.javatodev.finance.model.mapper;

import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.entity.BankAccountEntity;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_IDENTIFICATION_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aBankAccount;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUserEntity;
import static com.javatodev.finance.fixture.CoreBankingFixtures.anAccountEntity;
import static org.assertj.core.api.Assertions.assertThat;

class BankAccountMapperTest {

    private final BankAccountMapper mapper = new BankAccountMapper();

    @Test
    void convertToDto_entityWithUser_copiesAllFieldsExceptUser() {
        // Arrange
        BankAccountEntity entity = anAccountEntity(ACCOUNT_NUMBER_1, 200);
        entity.setUser(aUserEntity(USER_IDENTIFICATION_1, Collections.singletonList(entity)));

        // Act
        BankAccount dto = mapper.convertToDto(entity);

        // Assert
        assertThat(dto).usingRecursiveComparison().ignoringFields("user").isEqualTo(entity);
        assertThat(dto.getUser()).isNull();
    }

    @Test
    void convertToEntity_dtoWithUser_copiesAllFieldsExceptUser() {
        // Arrange
        BankAccount dto = aBankAccount(ACCOUNT_NUMBER_1, 200);

        // Act
        BankAccountEntity entity = mapper.convertToEntity(dto);

        // Assert
        assertThat(entity).usingRecursiveComparison().ignoringFields("user").isEqualTo(dto);
        assertThat(entity.getUser()).isNull();
    }

    @Test
    void convertToEntity_afterConvertToDto_roundTripsEveryField() {
        // Arrange
        BankAccountEntity original = anAccountEntity(ACCOUNT_NUMBER_1, 200);

        // Act
        BankAccountEntity roundTripped = mapper.convertToEntity(mapper.convertToDto(original));

        // Assert
        assertThat(roundTripped).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void convertToDto_nullEntity_returnsEmptyDto() {
        // Act
        BankAccount dto = mapper.convertToDto((BankAccountEntity) null);

        // Assert
        assertThat(dto).isNotNull().hasAllNullFieldsOrProperties();
    }

    @Test
    void convertToEntity_nullDto_returnsEmptyEntity() {
        // Act
        BankAccountEntity entity = mapper.convertToEntity((BankAccount) null);

        // Assert
        assertThat(entity).isNotNull().hasAllNullFieldsOrProperties();
    }

    @Test
    void convertToDtoList_twoEntities_mapsBothInOrder() {
        // Arrange
        List<BankAccountEntity> entities =
            List.of(anAccountEntity(ACCOUNT_NUMBER_1, 200), anAccountEntity(ACCOUNT_NUMBER_2, 50));

        // Act
        List<BankAccount> dtos = mapper.convertToDtoList(entities);

        // Assert
        assertThat(dtos).extracting(BankAccount::getNumber).containsExactly(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2);
    }

    @Test
    void convertToEntityList_twoDtos_mapsBothInOrder() {
        // Arrange
        List<BankAccount> dtos = List.of(aBankAccount(ACCOUNT_NUMBER_1, 200), aBankAccount(ACCOUNT_NUMBER_2, 50));

        // Act
        List<BankAccountEntity> entities = mapper.convertToEntityList(dtos);

        // Assert
        assertThat(entities).extracting(BankAccountEntity::getNumber).containsExactly(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2);
        assertThat(entities).extracting(BankAccountEntity::getUser).containsOnlyNulls();
    }

    @Test
    void convertToDtoSet_twoEntities_mapsBoth() {
        // Arrange
        List<BankAccountEntity> entities =
            List.of(anAccountEntity(ACCOUNT_NUMBER_1, 200), anAccountEntity(ACCOUNT_NUMBER_2, 50));

        // Act
        Set<BankAccount> dtos = mapper.convertToDtoSet(entities);

        // Assert
        assertThat(dtos).extracting(BankAccount::getNumber).containsExactlyInAnyOrder(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2);
    }

    @Test
    void collectionHelpers_emptyInput_returnEmptyCollections() {
        // Act & Assert
        assertThat(mapper.convertToDtoList(Collections.emptyList())).isEmpty();
        assertThat(mapper.convertToEntityList(Collections.emptyList())).isEmpty();
        assertThat(mapper.convertToDtoSet(Collections.emptyList())).isEmpty();
    }

}
