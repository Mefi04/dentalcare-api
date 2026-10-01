package com.dentalcare.api.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentDataSourceConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DataSourceAutoConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=dev",
                    "spring.datasource.url=jdbc:postgresql://localhost:6543/postgres",
                    "spring.datasource.username=test",
                    "spring.datasource.password=test"
            );

    @Test
    void disablesServerPreparedStatementsForTransactionPoolerCompatibility() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(javax.sql.DataSource.class);
            HikariDataSource dataSource = context.getBean(HikariDataSource.class);
            assertThat(dataSource.getDataSourceProperties())
                    .containsEntry("prepareThreshold", "0");
        });
    }
}
