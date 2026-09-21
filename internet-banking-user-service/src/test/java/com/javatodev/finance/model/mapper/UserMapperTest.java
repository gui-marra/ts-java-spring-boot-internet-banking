package com.javatodev.finance.model.mapper;

import com.javatodev.finance.fixture.UserFixtures;
import com.javatodev.finance.model.dto.Status;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.javatodev.finance.fixture.UserFixtures.AUTH_ID;
import static com.javatodev.finance.fixture.UserFixtures.USER_EMAIL;
import static com.javatodev.finance.fixture.UserFixtures.USER_IDENTIFICATION;
import static com.javatodev.finance.fixture.UserFixtures.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {
    private final UserMapper userMapper = new UserMapper();

    @Test
    void convertToEntity_dtoWithPassword_entityHasOnlySharedFields() {
        User dto = UserFixtures.aUser(1L, AUTH_ID, Status.PENDING);

        UserEntity entity = userMapper.convertToEntity(dto);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getAuthId()).isEqualTo(AUTH_ID);
        assertThat(entity.getIdentification()).isEqualTo(USER_IDENTIFICATION);
        assertThat(entity.getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void convertToDto_entityCopiesFieldsAndLeavesEmailAndPasswordNull() {
        User user = userMapper.convertToDto(UserFixtures.aUserEntity());

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getAuthId()).isEqualTo(AUTH_ID);
        assertThat(user.getIdentification()).isEqualTo(USER_IDENTIFICATION);
        assertThat(user.getStatus()).isEqualTo(Status.PENDING);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPassword()).isNull();
    }

    @Test
    void convertToDto_roundTripPreservesSharedFieldsAndDropsEmailAndPassword() {
        User original = UserFixtures.aUser(1L, AUTH_ID, Status.PENDING);

        User roundTrip = userMapper.convertToDto(userMapper.convertToEntity(original));

        assertThat(roundTrip).usingRecursiveComparison()
            .ignoringFields("email", "password")
            .isEqualTo(original);
        assertThat(roundTrip.getEmail()).isNull();
        assertThat(roundTrip.getPassword()).isNull();
    }

    @Test
    void convertToEntity_null_returnsEmptyEntity() {
        UserEntity entity = userMapper.convertToEntity((User) null);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getAuthId()).isNull();
        assertThat(entity.getIdentification()).isNull();
        assertThat(entity.getStatus()).isNull();
    }

    @Test
    void convertToDto_null_returnsEmptyDto() {
        User user = userMapper.convertToDto((UserEntity) null);

        assertThat(user.getId()).isNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPassword()).isNull();
        assertThat(user.getAuthId()).isNull();
        assertThat(user.getIdentification()).isNull();
        assertThat(user.getStatus()).isNull();
    }

    @Test
    void collectionApis_preserveOrderAndSetSemantics() {
        UserEntity e1 = UserFixtures.aUserEntity(1L, "A1", "I1", Status.PENDING);
        UserEntity e2 = UserFixtures.aUserEntity(2L, "A2", "I2", Status.APPROVED);

        assertThat(userMapper.convertToDtoList(List.of(e1, e2)))
            .extracting(User::getAuthId).containsExactly("A1", "A2");
        assertThat(userMapper.convertToEntityList(List.of(
            UserFixtures.aUser(1L, "A1", Status.PENDING),
            UserFixtures.aUser(2L, "A2", Status.APPROVED))))
            .extracting(UserEntity::getAuthId).containsExactly("A1", "A2");
        assertThat(userMapper.convertToDtoSet(List.of(e1, e2))).hasSize(2);
        assertThat(userMapper.convertToEntitySet(List.of(
            UserFixtures.aUser(1L, "A1", Status.PENDING),
            UserFixtures.aUser(2L, "A2", Status.APPROVED)))).hasSize(2);
        assertThat(userMapper.convertToDtoSet(List.of(
            UserFixtures.aUserEntity(), UserFixtures.aUserEntity()))).hasSize(1);
        assertThat(userMapper.convertToEntitySet(List.of(
            UserFixtures.aUser(), UserFixtures.aUser()))).hasSize(2);
        Collection<User> users = userMapper.convertToDto(List.of(e1, e2));
        assertThat(users).hasSize(2);
    }
}
