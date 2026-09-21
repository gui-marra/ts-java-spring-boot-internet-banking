package com.javatodev.finance.model.mapper;

import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.UserEntity;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_IDENTIFICATION_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aBankAccount;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUser;
import static com.javatodev.finance.fixture.CoreBankingFixtures.aUserEntity;
import static com.javatodev.finance.fixture.CoreBankingFixtures.anAccountEntity;
import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void convertToDto_entityWithAccounts_copiesFieldsAndMapsAccounts() {
        // Arrange
        UserEntity entity = aUserEntity(USER_IDENTIFICATION_1,
            List.of(anAccountEntity(ACCOUNT_NUMBER_1, 200), anAccountEntity(ACCOUNT_NUMBER_2, 50)));

        // Act
        User dto = mapper.convertToDto(entity);

        // Assert
        assertThat(dto).usingRecursiveComparison().ignoringFields("bankAccounts").isEqualTo(entity);
        assertThat(dto.getBankAccounts())
            .usingRecursiveComparison().ignoringFields("user")
            .isEqualTo(entity.getAccounts());
        assertThat(dto.getBankAccounts()).extracting(BankAccount::getNumber).containsExactly(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2);
        assertThat(dto.getBankAccounts()).extracting(BankAccount::getUser).containsOnlyNulls();
    }

    @Test
    void convertToEntity_dtoWithAccounts_copiesFieldsAndMapsAccounts() {
        // Arrange
        User dto = aUser(USER_IDENTIFICATION_1,
            List.of(aBankAccount(ACCOUNT_NUMBER_1, 200), aBankAccount(ACCOUNT_NUMBER_2, 50)));

        // Act
        UserEntity entity = mapper.convertToEntity(dto);

        // Assert
        assertThat(entity).usingRecursiveComparison().ignoringFields("accounts").isEqualTo(dto);
        assertThat(entity.getAccounts()).extracting(BankAccountEntity::getNumber).containsExactly(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2);
        assertThat(entity.getAccounts()).extracting(BankAccountEntity::getUser).containsOnlyNulls();
    }

    @Test
    void convertToEntity_afterConvertToDto_roundTripsEveryField() {
        // Arrange
        UserEntity original = aUserEntity(USER_IDENTIFICATION_1,
            List.of(anAccountEntity(ACCOUNT_NUMBER_1, 200), anAccountEntity(ACCOUNT_NUMBER_2, 50)));

        // Act
        UserEntity roundTripped = mapper.convertToEntity(mapper.convertToDto(original));

        // Assert
        assertThat(roundTripped).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void convertToDto_nullEntity_returnsEmptyDto() {
        // Act
        User dto = mapper.convertToDto((UserEntity) null);

        // Assert
        assertThat(dto).isNotNull().hasAllNullFieldsOrProperties();
    }

    @Test
    void convertToEntity_nullDto_returnsEmptyEntity() {
        // Act
        UserEntity entity = mapper.convertToEntity((User) null);

        // Assert
        assertThat(entity).isNotNull().hasAllNullFieldsOrProperties();
    }

    @Disabled("Divergence: UserMapper NPEs when entity.accounts is null — see issue #TBD")
    @Test
    void convertToDto_entityWithNullAccounts_returnsEmptyBankAccounts() {
        // Arrange
        UserEntity entity = aUserEntity(USER_IDENTIFICATION_1, null);

        // Act
        User dto = mapper.convertToDto(entity);

        // Assert
        assertThat(dto.getIdentificationNumber()).isEqualTo(USER_IDENTIFICATION_1);
        assertThat(dto.getBankAccounts()).isEmpty();
    }

    @Disabled("Divergence: UserMapper NPEs when dto.bankAccounts is null — see issue #TBD")
    @Test
    void convertToEntity_dtoWithNullBankAccounts_returnsEmptyAccounts() {
        // Arrange
        User dto = aUser(USER_IDENTIFICATION_1, null);

        // Act
        UserEntity entity = mapper.convertToEntity(dto);

        // Assert
        assertThat(entity.getIdentificationNumber()).isEqualTo(USER_IDENTIFICATION_1);
        assertThat(entity.getAccounts()).isEmpty();
    }

    @Test
    void convertToDtoList_twoEntities_mapsBothInOrder() {
        // Arrange
        UserEntity second = aUserEntity("123456789V", Collections.emptyList());
        second.setId(2L);
        List<UserEntity> entities = List.of(aUserEntity(USER_IDENTIFICATION_1, Collections.emptyList()), second);

        // Act
        List<User> dtos = mapper.convertToDtoList(entities);

        // Assert
        assertThat(dtos).extracting(User::getIdentificationNumber).containsExactly(USER_IDENTIFICATION_1, "123456789V");
    }

    @Test
    void convertToEntityList_twoDtos_mapsBothInOrder() {
        // Arrange
        User second = aUser("123456789V", Collections.emptyList());
        second.setId(2L);
        List<User> dtos = List.of(aUser(USER_IDENTIFICATION_1, Collections.emptyList()), second);

        // Act
        List<UserEntity> entities = mapper.convertToEntityList(dtos);

        // Assert
        assertThat(entities).extracting(UserEntity::getId).containsExactly(1L, 2L);
    }

    @Test
    void convertToDtoSet_twoEntities_mapsBoth() {
        // Arrange
        UserEntity second = aUserEntity("123456789V", Collections.emptyList());
        second.setId(2L);
        List<UserEntity> entities = List.of(aUserEntity(USER_IDENTIFICATION_1, Collections.emptyList()), second);

        // Act
        Set<User> dtos = mapper.convertToDtoSet(entities);

        // Assert
        assertThat(dtos).extracting(User::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void collectionHelpers_emptyInput_returnEmptyCollections() {
        // Act & Assert
        assertThat(mapper.convertToDtoList(Collections.emptyList())).isEmpty();
        assertThat(mapper.convertToEntityList(Collections.emptyList())).isEmpty();
        assertThat(mapper.convertToDtoSet(Collections.emptyList())).isEmpty();
    }

}
