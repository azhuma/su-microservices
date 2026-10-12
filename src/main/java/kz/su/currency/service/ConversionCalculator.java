package kz.su.currency.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import kz.su.currency.api.ConversionResponse;
import kz.su.currency.rates.ExchangeRate;

public class ConversionCalculator {
    public ConversionResponse convert(Currency from, Currency to, BigDecimal amount,
                                      ExchangeRate source, ExchangeRate target) {
        if (from.equals(to)) {
            return new ConversionResponse(from.getCurrencyCode(), to.getCurrencyCode(), amount,
                    BigDecimal.ONE.setScale(10), roundAmount(amount, to), null, null);
        }

        BigDecimal exactRate = source.kztPerUnit().divide(target.kztPerUnit(), MathContext.DECIMAL128);
        // Use the unrounded rate for the amount; round the response rate only for display.
        BigDecimal converted = amount.multiply(exactRate, MathContext.DECIMAL128);
        return new ConversionResponse(from.getCurrencyCode(), to.getCurrencyCode(), amount,
                exactRate.setScale(10, RoundingMode.HALF_UP), roundAmount(converted, to),
                source.rateDate(), source.fetchedOn());
    }

    private BigDecimal roundAmount(BigDecimal amount, Currency target) {
        return amount.setScale(target.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    }
}
