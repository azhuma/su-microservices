package kz.su.currency.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import kz.su.currency.api.ApiException;
import kz.su.currency.api.ConversionRequest;
import kz.su.currency.api.ConversionResponse;
import kz.su.currency.rates.ExchangeRate;
import kz.su.currency.rates.RateRepository;
import kz.su.currency.rates.RatesProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConversionService {
    private final ConversionCalculator calculator = new ConversionCalculator();
    private final CurrencyResolver resolver;
    private final RateRepository repository;
    private final RatesProvider provider;
    private final Clock clock;

    public ConversionService(CurrencyResolver resolver, RateRepository repository,
                             RatesProvider provider, Clock clock) {
        this.resolver = resolver;
        this.repository = repository;
        this.provider = provider;
        this.clock = clock;
    }

    // One lock lets concurrent requests share a successful refresh in this single-instance application.
    public synchronized ConversionResponse convert(ConversionRequest request) {
        Currency from = resolve(request.from());
        Currency to = resolve(request.to());
        if (from.equals(to)) {
            return calculator.convert(from, to, request.amount(), null, null);
        }

        LocalDate today = LocalDate.now(clock);
        List<ExchangeRate> rates = repository.findAll();
        ExchangeRate source = find(rates, from);
        ExchangeRate target = find(rates, to);
        if (source == null || target == null || !source.fetchedOn().equals(today)
                || !target.fetchedOn().equals(today) || !source.rateDate().equals(target.rateDate())) {
            rates = refresh(today);
            source = find(rates, from);
            target = find(rates, to);
        }
        if (source == null || target == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RATE_NOT_FOUND",
                    "Provider has no rate for " + (source == null ? from.getCurrencyCode() : to.getCurrencyCode()));
        }
        return calculator.convert(from, to, request.amount(), source, target);
    }

    private Currency resolve(String input) {
        try {
            return resolver.resolve(input);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY", exception.getMessage());
        }
    }

    private List<ExchangeRate> refresh(LocalDate today) {
        List<ExchangeRate> rates = provider.fetch(today);
        repository.replaceAll(rates);
        return rates;
    }

    private ExchangeRate find(List<ExchangeRate> rates, Currency currency) {
        return rates.stream().filter(rate -> rate.currencyCode().equals(currency.getCurrencyCode()))
                .findFirst().orElse(null);
    }
}
