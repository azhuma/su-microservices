package kz.su.currency.rates;

import java.sql.Date;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RateRepository {
    private final JdbcTemplate jdbc;

    public RateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ExchangeRate> findAll() {
        return jdbc.query("SELECT currency_code, numeric_code, kzt_per_unit, rate_date, fetched_on FROM exchange_rate",
                (row, index) -> new ExchangeRate(row.getString("currency_code"),
                        row.getString("numeric_code"), row.getBigDecimal("kzt_per_unit"),
                        row.getDate("rate_date").toLocalDate(), row.getDate("fetched_on").toLocalDate()));
    }

    @Transactional
    public void replaceAll(List<ExchangeRate> rates) {
        jdbc.update("DELETE FROM exchange_rate");
        jdbc.batchUpdate("""
                INSERT INTO exchange_rate(currency_code, numeric_code, kzt_per_unit, rate_date, fetched_on)
                VALUES (?, ?, ?, ?, ?)
                """, rates, rates.size(), (statement, rate) -> {
            statement.setString(1, rate.currencyCode());
            statement.setString(2, rate.numericCode());
            statement.setBigDecimal(3, rate.kztPerUnit());
            statement.setDate(4, Date.valueOf(rate.rateDate()));
            statement.setDate(5, Date.valueOf(rate.fetchedOn()));
        });
    }
}
