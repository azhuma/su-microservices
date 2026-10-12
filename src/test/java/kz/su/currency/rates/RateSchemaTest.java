package kz.su.currency.rates;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateSchemaTest {
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 10, 2);
    private static final LocalDate FETCHED_ON = LocalDate.of(2026, 10, 5);

    @Test
    void createsKztSchemaAndPreservesRatesOnRepeatedInitialization() throws SQLException {
        // Реальная H2 проверяет SQL, ограничения и совместимость схемы с репозиторием.
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:new_kzt_schema", "sa", "")) {
            JdbcTemplate jdbc = jdbc(connection);
            initialize(connection);
            RateRepository repository = new RateRepository(jdbc);
            List<ExchangeRate> expected = List.of(rate("USD", "840", "500.000000000000000000"),
                    rate("KZT", "398", "1.000000000000000000"));
            repository.replaceAll(expected);
            initialize(connection);
            assertAll(
                    () -> assertEquals(Set.copyOf(expected), Set.copyOf(repository.findAll())),
                    () -> assertThrows(DataIntegrityViolationException.class,
                            () -> jdbc.update("UPDATE exchange_rate SET kzt_per_unit = 0 WHERE currency_code = 'USD'")),
                    () -> assertThrows(DataIntegrityViolationException.class,
                            () -> jdbc.update("UPDATE exchange_rate SET kzt_per_unit = NULL WHERE currency_code = 'USD'")));
        }
    }

    @Test
    void migratesRubCacheToKztWithoutChangingDatesOrRebasingTwice() throws SQLException {
        // Старые курсы должны пересчитаться через RUB за KZT; повторный запуск сохраняет результат.
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:legacy_rub_schema", "sa", "")) {
            JdbcTemplate jdbc = jdbc(connection);
            createLegacySchema(jdbc);
            insertLegacy(jdbc, "USD", "840", "90");
            insertLegacy(jdbc, "RUB", "643", "1");
            insertLegacy(jdbc, "KZT", "398", "0.18");
            initialize(connection);
            RateRepository repository = new RateRepository(jdbc);
            List<ExchangeRate> migrated = repository.findAll();
            assertAll(
                    () -> assertEquals(3, migrated.size()),
                    () -> assertTrue(migrated.contains(rate("USD", "840", "500.000000000000000000"))),
                    () -> assertTrue(migrated.contains(rate("RUB", "643", "5.555555555555555556"))),
                    () -> assertTrue(migrated.contains(rate("KZT", "398", "1.000000000000000000"))),
                    () -> assertEquals(0, jdbc.queryForObject("""
                            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                            WHERE TABLE_NAME = 'EXCHANGE_RATE' AND COLUMN_NAME = 'RUB_PER_UNIT'
                            """, Integer.class)));
            initialize(connection);
            assertEquals(Set.copyOf(migrated), Set.copyOf(repository.findAll()));
        }
    }

    @Test
    void invalidatesLegacyCacheWithoutKztSoItCanBeReloaded() throws SQLException {
        // Без KZT старую базу пересчитать нельзя: пустой кеш вызовет обычную загрузку источника.
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:incomplete_rub_schema", "sa", "")) {
            JdbcTemplate jdbc = jdbc(connection);
            createLegacySchema(jdbc);
            insertLegacy(jdbc, "USD", "840", "90");
            initialize(connection);
            assertTrue(new RateRepository(jdbc).findAll().isEmpty());
            initialize(connection);
            new RateRepository(jdbc).replaceAll(List.of(rate("KZT", "398", "1.000000000000000000")));
            assertEquals(1, new RateRepository(jdbc).findAll().size());
        }
    }

    private JdbcTemplate jdbc(Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    private void initialize(Connection connection) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
    }

    private void createLegacySchema(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE exchange_rate (
                    currency_code VARCHAR(3) PRIMARY KEY,
                    numeric_code VARCHAR(3) NOT NULL,
                    rub_per_unit DECIMAL(38,18) NOT NULL CHECK (rub_per_unit > 0),
                    rate_date DATE NOT NULL,
                    fetched_on DATE NOT NULL
                )
                """);
    }

    private void insertLegacy(JdbcTemplate jdbc, String code, String numericCode, String rubPerUnit) {
        jdbc.update("INSERT INTO exchange_rate VALUES (?, ?, ?, ?, ?)",
                code, numericCode, new BigDecimal(rubPerUnit), RATE_DATE, FETCHED_ON);
    }

    private ExchangeRate rate(String code, String numericCode, String kztPerUnit) {
        return new ExchangeRate(code, numericCode, new BigDecimal(kztPerUnit), RATE_DATE, FETCHED_ON);
    }
}
