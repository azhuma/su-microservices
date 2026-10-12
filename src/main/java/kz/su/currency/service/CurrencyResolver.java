package kz.su.currency.service;

import java.util.Currency;
import java.util.Locale;

public class CurrencyResolver {
    public Currency resolve(String input) {
        String code = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        if (code.matches("[0-9]{3}")) {
            return Currency.getAvailableCurrencies().stream()
                    .filter(currency -> currency.getDefaultFractionDigits() >= 0)
                    .filter(currency -> currency.getNumericCodeAsString().equals(code))
                    .findFirst().orElseThrow(() -> invalid(input));
        }
        if (code.matches("[A-Z]{3}")) {
            try {
                Currency currency = Currency.getInstance(code);
                if (currency.getDefaultFractionDigits() >= 0) {
                    return currency;
                }
            } catch (IllegalArgumentException ignored) {
                // The same public error is used for unknown alpha and numeric codes.
            }
        }
        throw invalid(input);
    }

    private IllegalArgumentException invalid(String input) {
        return new IllegalArgumentException("Unknown ISO 4217 currency code: " + input);
    }
}
