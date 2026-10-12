package kz.su.currency.rates;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExchangeRateApiParserTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final String JSON = """
            {"result":"success","base_code":"USD","time_last_update_unix":1790899200,
             "rates":{"USD":1,"RUB":90,"KZT":500,"EUR":3.000000000000000001,"CNH":7.1}}
            """;
    private final ExchangeRateApiParser parser = new ExchangeRateApiParser();

    @Test
    void normalizesUsdQuotesToKztUnitsAndPreservesDates() {
        // TC-05, TC-07: источник котирует валюты за USD, а БД хранит KZT за единицу.
        // KZT проверяет базовый курс; RUB, EUR и CNH — кросс-курс, точность и ISO-коды.
        var rates = parser.parse(bytes(JSON.replace("\"USD\":1", "\"USD\":1.0")), TODAY);
        ExchangeRate usd = rates.stream().filter(rate -> rate.currencyCode().equals("USD")).findFirst().orElseThrow();
        ExchangeRate kzt = rates.stream().filter(rate -> rate.currencyCode().equals("KZT")).findFirst().orElseThrow();
        ExchangeRate rub = rates.stream().filter(rate -> rate.currencyCode().equals("RUB")).findFirst().orElseThrow();
        ExchangeRate eur = rates.stream().filter(rate -> rate.currencyCode().equals("EUR")).findFirst().orElseThrow();
        assertAll(
                () -> assertEquals(4, rates.size()),
                () -> assertEquals(new BigDecimal("500.000000000000000000"), usd.kztPerUnit()),
                () -> assertEquals(new BigDecimal("1.000000000000000000"), kzt.kztPerUnit()),
                () -> assertEquals(new BigDecimal("5.555555555555555556"), rub.kztPerUnit()),
                () -> assertEquals(new BigDecimal("166.666666666666666611"), eur.kztPerUnit()),
                () -> assertEquals("398", kzt.numericCode()),
                () -> assertEquals(LocalDate.of(2026, 10, 2), kzt.rateDate()),
                () -> assertEquals(TODAY, kzt.fetchedOn()));
    }

    @Test
    void acceptsSnapshotWithoutRub() {
        // RUB больше не является обязательной базовой котировкой.
        var rates = parser.parse(bytes(JSON.replace("\"RUB\":90,", "")), TODAY);
        assertAll(
                () -> assertEquals(3, rates.size()),
                () -> assertEquals(new BigDecimal("1.000000000000000000"), rates.stream()
                        .filter(rate -> rate.currencyCode().equals("KZT")).findFirst().orElseThrow().kztPerUnit()));
    }

    @Test
    void rejectsInvalidSnapshotBeforeItCanBeStored() {
        // Дополнение к TC-10: неверный ответ публичного источника нельзя задать вручную.
        // Поэтому ошибки JSON, статуса, базы, даты и чисел проверяются здесь без сети;
        // весь набор должен отклоняться до записи в БД.
        String[] invalid = {
                "", "{broken", JSON + "{}",
                JSON.replace("success", "error"), JSON.replace("\"base_code\":\"USD\"", "\"base_code\":\"EUR\""),
                JSON.replace("1790899200", "1791244800"), JSON.replace("1790899200", "0"),
                JSON.replace("\"USD\":1", "\"USD\":2"), JSON.replace("\"USD\":1,", ""),
                JSON.replace("\"KZT\":500,", ""),
                JSON.replace("\"KZT\":500", "\"KZT\":0"),
                JSON.replace("\"KZT\":500", "\"KZT\":\"500\""),
                JSON.replace("\"KZT\":500", "\"KZT\":0.000000000000000000001"),
                JSON.replace("\"KZT\":500", "\"KZT\":100000000000000000000"),
                JSON.replace("\"USD\":1", "\"USD\":1,\"USD\":1")
        };
        for (String json : invalid) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(bytes(json), TODAY), json);
        }
    }

    private byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
