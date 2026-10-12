package kz.su.currency.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.stream.Stream;
import kz.su.currency.api.ApiException;
import kz.su.currency.api.ConversionRequest;
import kz.su.currency.rates.ExchangeRate;
import kz.su.currency.rates.RateRepository;
import kz.su.currency.rates.RatesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversionServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate RATE_DATE = TODAY.minusDays(1);
    private final RateRepository repository = mock(RateRepository.class);
    private final RatesProvider provider = mock(RatesProvider.class);
    private final ConversionService service = new ConversionService(
            new CurrencyResolver(), repository, provider,
            // В UTC ещё вчера: запрос должен использовать календарный день Asia/Almaty.
            Clock.fixed(Instant.parse("2026-10-11T20:00:00Z"), ZoneId.of("Asia/Almaty")));

    @Test
    void convertsSameCurrencyWithoutCallingRepositoryOrProvider() {
        // TC-03: настоящий сервис должен обойти зависимости после нормализации USD/840.
        var response = service.convert(new ConversionRequest(" usd ", "840", new BigDecimal("12.345")));

        assertAll(
                () -> assertEquals("USD", response.from()),
                () -> assertEquals("USD", response.to()),
                () -> assertEquals(new BigDecimal("1.0000000000"), response.rate()),
                () -> assertEquals(new BigDecimal("12.35"), response.convertedAmount()),
                () -> assertNull(response.rateDate()),
                () -> assertNull(response.fetchedOn()));
        verifyNoInteractions(repository, provider);
    }

    @Test
    void usesFreshCacheWithoutCallingProviderOrWritingToDatabase() {
        // TC-05, TC-07: вчерашняя публикация при сегодняшней загрузке остаётся актуальной.
        when(repository.findAll()).thenReturn(snapshot(TODAY, "500"));

        var response = service.convert(request());

        assertAll(
                () -> assertEquals(new BigDecimal("50000.00"), response.convertedAmount()),
                () -> assertEquals(RATE_DATE, response.rateDate()),
                () -> assertEquals(TODAY, response.fetchedOn()));
        verify(repository).findAll();
        verifyNoInteractions(provider);
        verify(repository, never()).replaceAll(anyList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cachesRequiringRefresh")
    void refreshesInvalidCacheAndCalculatesUsingNewRates(String scenario, List<ExchangeRate> cached) {
        // TC-05, TC-06: каждый независимый повод обновления должен выбрать новый курс 500 вместо 480.
        var fresh = snapshot(TODAY, "500");
        when(repository.findAll()).thenReturn(cached);
        when(provider.fetch(TODAY)).thenReturn(fresh);

        var response = service.convert(request());

        assertAll(
                () -> assertEquals(new BigDecimal("500.0000000000"), response.rate()),
                () -> assertEquals(new BigDecimal("50000.00"), response.convertedAmount()),
                () -> assertEquals(RATE_DATE, response.rateDate()),
                () -> assertEquals(TODAY, response.fetchedOn()));
        verify(repository).findAll();
        verify(provider).fetch(TODAY);
        verify(repository).replaceAll(fresh);
    }

    @Test
    void doesNotReplaceCacheWhenProviderFails() {
        // TC-10: ошибка загрузки не должна приводить к записи или ответу по устаревшему курсу.
        when(repository.findAll()).thenReturn(snapshot(TODAY.minusDays(1), "480"));
        var failure = new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "PROVIDER_UNAVAILABLE", "Provider unavailable");
        when(provider.fetch(TODAY)).thenThrow(failure);

        var exception = assertThrows(ApiException.class, () -> service.convert(request()));

        assertSame(failure, exception);
        verify(provider).fetch(TODAY);
        verify(repository, never()).replaceAll(anyList());
    }

    @Test
    void reportsMissingRequestedRateAfterSavingValidProviderSnapshot() {
        // TC-09: EUR отсутствует даже после загрузки; валидный набор остальных валют сохраняется.
        var fresh = snapshot(TODAY, "500");
        when(repository.findAll()).thenReturn(List.of());
        when(provider.fetch(TODAY)).thenReturn(fresh);

        var exception = assertThrows(ApiException.class, () -> service.convert(
                new ConversionRequest("EUR", "KZT", BigDecimal.ONE)));

        assertAll(
                () -> assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.status()),
                () -> assertEquals("RATE_NOT_FOUND", exception.code()));
        verify(provider).fetch(TODAY);
        verify(repository).replaceAll(fresh);
    }

    @ParameterizedTest
    @CsvSource({"ZZZ,KZT", "USD,ZZZ"})
    void rejectsInvalidCurrencyWithoutCallingRepositoryOrProvider(String from, String to) {
        // TC-08: ошибка любого из двух кодов должна обнаруживаться до чтения кеша.
        var exception = assertThrows(ApiException.class, () -> service.convert(
                new ConversionRequest(from, to, BigDecimal.ONE)));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.status()),
                () -> assertEquals("INVALID_CURRENCY", exception.code()));
        verifyNoInteractions(repository, provider);
    }

    private static Stream<Arguments> cachesRequiringRefresh() {
        var usd = rate("USD", "480", RATE_DATE, TODAY);
        var kzt = rate("KZT", "1", RATE_DATE, TODAY);
        return Stream.of(
                Arguments.of("Empty cache", List.of()),
                Arguments.of("Missing source currency", List.of(kzt)),
                Arguments.of("Missing target currency", List.of(usd)),
                Arguments.of("Expired source rate", List.of(
                        rate("USD", "480", RATE_DATE, TODAY.minusDays(1)), kzt)),
                Arguments.of("Expired target rate", List.of(
                        usd, rate("KZT", "1", RATE_DATE, TODAY.minusDays(1)))),
                Arguments.of("Different publication dates", List.of(
                        usd, rate("KZT", "1", RATE_DATE.minusDays(1), TODAY))));
    }

    private ConversionRequest request() {
        return new ConversionRequest("USD", "KZT", new BigDecimal("100"));
    }

    private static List<ExchangeRate> snapshot(LocalDate fetchedOn, String usdRate) {
        return List.of(rate("USD", usdRate, RATE_DATE, fetchedOn),
                rate("KZT", "1", RATE_DATE, fetchedOn));
    }

    private static ExchangeRate rate(String code, String value, LocalDate rateDate, LocalDate fetchedOn) {
        return new ExchangeRate(code, Currency.getInstance(code).getNumericCodeAsString(),
                new BigDecimal(value), rateDate, fetchedOn);
    }
}
