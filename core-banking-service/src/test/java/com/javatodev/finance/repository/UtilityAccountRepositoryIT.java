package com.javatodev.finance.repository;

import com.javatodev.finance.MySqlTestcontainerConfig;
import com.javatodev.finance.model.entity.UtilityAccountEntity;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_ACCOUNT_VODAFONE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE;
import static com.javatodev.finance.fixture.CoreBankingFixtures.UTILITY_PROVIDER_VODAFONE_ID;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestcontainerConfig.class)
@Tag("integration")
class UtilityAccountRepositoryIT {

    @Autowired
    private UtilityAccountRepository utilityAccountRepository;

    @Test
    void findByProviderName_seededProvider_returnsExpectedUtilityAccount() {
        // Arrange

        // Act
        UtilityAccountEntity result =
            utilityAccountRepository.findByProviderName(UTILITY_PROVIDER_VODAFONE).orElseThrow();

        // Assert
        assertThat(result.getId()).isEqualTo(UTILITY_PROVIDER_VODAFONE_ID);
        assertThat(result.getNumber()).isEqualTo(UTILITY_ACCOUNT_VODAFONE);
    }

    @Test
    void findByProviderName_unknownProvider_returnsEmpty() {
        // Arrange

        // Act
        var result = utilityAccountRepository.findByProviderName("UNKNOWN");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findByProviderName_lowercaseProvider_returnsSeededProviderBecauseCollationIsCaseInsensitive() {
        // Arrange

        // Act
        var result = utilityAccountRepository.findByProviderName("vodafone");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getId()).isEqualTo(UTILITY_PROVIDER_VODAFONE_ID);
    }

    @Test
    void findByProviderName_nullProvider_returnsEmpty() {
        // Arrange

        // Act
        var result = utilityAccountRepository.findByProviderName(null);

        // Assert
        assertThat(result).isEmpty();
    }
}
