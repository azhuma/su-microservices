package kz.su.currency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresCurrencyApplication {
    public static void main(String[] args) {
        SpringApplication.from(CurrencyApplication::main)
                .with(PostgresConfiguration.class)
                .withAdditionalProfiles("postgres")
                .run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("currency")
                    .withUsername("currency")
                    .withPassword("currency");
        }
    }
}
