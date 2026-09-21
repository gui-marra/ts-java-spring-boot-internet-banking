package com.javatodev.finance.repository;

import com.javatodev.finance.MySqlTestcontainerConfig;
import com.javatodev.finance.model.entity.UserEntity;

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

import java.util.ArrayList;

import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_1;
import static com.javatodev.finance.fixture.CoreBankingFixtures.ACCOUNT_NUMBER_2;
import static com.javatodev.finance.fixture.CoreBankingFixtures.USER_EMAIL_1;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestcontainerConfig.class)
@Tag("integration")
class UserRepositoryIT {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByIdentificationNumber_seededUser_returnsUserAndAccounts() {
        // Arrange

        // Act
        UserEntity result = userRepository.findByIdentificationNumber("808829932V").orElseThrow();

        // Assert
        assertThat(result.getFirstName()).isEqualTo("Sam");
        assertThat(result.getLastName()).isEqualTo("Silva");
        assertThat(result.getEmail()).isEqualTo(USER_EMAIL_1);
        assertThat(result.getAccounts()).extracting(account -> account.getNumber())
            .containsExactlyInAnyOrder(ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2);
    }

    @Test
    void findByIdentificationNumber_newUserWithoutAccounts_returnsEmptyAccounts() {
        // Arrange
        UserEntity user = new UserEntity();
        user.setFirstName("New");
        user.setLastName("User");
        user.setEmail("new-user@example.com");
        user.setIdentificationNumber("900000000V");
        user.setAccounts(new ArrayList<>());
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        // Act
        UserEntity result = userRepository.findByIdentificationNumber("900000000V").orElseThrow();

        // Assert
        assertThat(result.getAccounts()).isEmpty();
    }

    @Test
    void findByIdentificationNumber_unknownIdentificationNumber_returnsEmpty() {
        // Arrange

        // Act
        var result = userRepository.findByIdentificationNumber("UNKNOWN");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findByIdentificationNumber_nullIdentificationNumber_returnsEmpty() {
        // Arrange

        // Act
        var result = userRepository.findByIdentificationNumber(null);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findAll_firstPageSortedById_returnsFirstTwoOfFourUsers() {
        // Arrange

        // Act
        var result = userRepository.findAll(PageRequest.of(0, 2, Sort.by("id")));

        // Assert
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getContent()).extracting(UserEntity::getId).containsExactly(1L, 2L);
    }
}
