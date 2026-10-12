package kz.su.currency.rates;

import java.time.LocalDate;
import java.util.List;

public interface RatesProvider {
    List<ExchangeRate> fetch(LocalDate date);
}
