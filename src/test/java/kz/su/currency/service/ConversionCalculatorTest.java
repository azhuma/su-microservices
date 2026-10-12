package kz.su.currency.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import kz.su.currency.api.ConversionResponse;
import kz.su.currency.rates.ExchangeRate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConversionCalculatorTest {
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 10, 2);
    private static final LocalDate FETCHED_ON = LocalDate.of(2026, 10, 5);
    private final ConversionCalculator calculator = new ConversionCalculator();

    @Test
    void calculatesCrossReverseAndBaseRates() {
        // TC-01, TC-02: прямое и обратное направления выявляют перепутанное деление,
        // пары с KZT проверяют базовый курс; RUB остаётся доступен как обычная валюта.
        var direct = convert("USD", "KZT", "100", "500", "1");
        var reverse = convert("KZT", "USD", "500", "1", "500");
        var toRub = convert("USD", "RUB", "2", "500", "5.555555555555555556");
        var fromRub = convert("RUB", "USD", "180", "5.555555555555555556", "500");
        assertAll(
                () -> assertEquals(new BigDecimal("500.0000000000"), direct.rate()),
                () -> assertEquals(new BigDecimal("50000.00"), direct.convertedAmount()),
                () -> assertEquals("USD", direct.from()),
                () -> assertEquals("KZT", direct.to()),
                () -> assertEquals(new BigDecimal("100"), direct.amount()),
                () -> assertEquals(RATE_DATE, direct.rateDate()),
                () -> assertEquals(FETCHED_ON, direct.fetchedOn()),
                () -> assertEquals(new BigDecimal("0.0020000000"), reverse.rate()),
                () -> assertEquals(new BigDecimal("1.00"), reverse.convertedAmount()),
                () -> assertEquals(new BigDecimal("180.00"), toRub.convertedAmount()),
                () -> assertEquals(new BigDecimal("2.00"), fromRub.convertedAmount()));
    }

    @Test
    void convertsSameCurrencyWithoutRates() {
        // TC-03: тождественный расчёт известен без котировок и возвращает null-даты.
        // null здесь означает отсутствие входных курсов, а не подмену репозитория/источника.
        var usd = Currency.getInstance("USD");
        var result = calculator.convert(usd, usd, new BigDecimal("12.345"), null, null);
        assertAll(
                () -> assertEquals(new BigDecimal("1.0000000000"), result.rate()),
                () -> assertEquals(new BigDecimal("12.35"), result.convertedAmount()),
                () -> assertNull(result.rateDate()),
                () -> assertNull(result.fetchedOn()));
    }

    @Test
    void roundsHalfUpWithCurrencyFractionDigitsAndAcceptsZero() {
        // TC-04: JPY/USD/KWD представляют 0/2/3 разряда и защищают от жёстких двух знаков.
        // Значения ровно посередине различают HALF_UP и HALF_EVEN; ноль — нижняя граница.
        assertAll(
                () -> assertEquals(new BigDecimal("3"), convert("USD", "JPY", "2.5", "500", "500").convertedAmount()),
                () -> assertEquals(new BigDecimal("1.01"), convert("EUR", "USD", "1.005", "500", "500").convertedAmount()),
                () -> assertEquals(new BigDecimal("1.235"), convert("USD", "KWD", "1.2345", "500", "500").convertedAmount()),
                () -> assertEquals(new BigDecimal("0.00"), convert("USD", "KZT", "0", "500", "1").convertedAmount()));
    }

    @Test
    void calculatesAmountBeforeRoundingDisplayedRate() {
        // TC-04: периодическая дробь 1/3 на большой сумме делает раннее округление заметным.
        // Без отдельного теста основные примеры с точными курсами не обнаружат эту ошибку.
        var result = convert("USD", "EUR", "100000000000", "500", "1500");
        assertAll(
                () -> assertEquals(new BigDecimal("0.3333333333"), result.rate()),
                () -> assertEquals(new BigDecimal("33333333333.33"), result.convertedAmount()));
    }

    private ConversionResponse convert(String from, String to, String amount, String sourceRate, String targetRate) {
        return calculator.convert(Currency.getInstance(from), Currency.getInstance(to), new BigDecimal(amount),
                rate(from, sourceRate), rate(to, targetRate));
    }

    private ExchangeRate rate(String code, String value) {
        return new ExchangeRate(code, Currency.getInstance(code).getNumericCodeAsString(),
                new BigDecimal(value), RATE_DATE, FETCHED_ON);
    }
}
