package kz.su.currency.rates;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRate(String currencyCode, String numericCode,
                           BigDecimal kztPerUnit, LocalDate rateDate, LocalDate fetchedOn) {
}
