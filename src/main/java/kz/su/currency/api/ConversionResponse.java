package kz.su.currency.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionResponse(String from, String to, BigDecimal amount,
                                 BigDecimal rate, BigDecimal convertedAmount,
                                 LocalDate rateDate, LocalDate fetchedOn) {
}
