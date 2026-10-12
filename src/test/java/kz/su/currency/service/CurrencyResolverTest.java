package kz.su.currency.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyResolverTest {
    private final CurrencyResolver resolver = new CurrencyResolver();

    @Test
    void resolvesSupportedCodeFormats() {
        // TC-01, TC-09: буквенный, цифровой и смешанный ввод должны распознаваться одинаково.
        // Выбраны представители форматов; 008 отдельно защищает сохранение ведущего нуля.
        assertAll(
                () -> assertEquals("USD", resolver.resolve("USD").getCurrencyCode()),
                () -> assertEquals("USD", resolver.resolve("840").getCurrencyCode()),
                () -> assertEquals("KZT", resolver.resolve(" kzt ").getCurrencyCode()),
                () -> assertEquals("KZT", resolver.resolve("398").getCurrencyCode()),
                () -> assertEquals("ALL", resolver.resolve("008").getCurrencyCode()));
    }

    @Test
    void rejectsInvalidAndNonMonetaryCodes() {
        // TC-08: по одному представителю отсутствия ввода, неверного формата,
        // неизвестного буквенного/цифрового кода и немонетарной валюты.
        // HTTP 400 проверяется вручную: здесь проверяется только распознавание ISO-кодов.
        String[] inputs = {null, " ", "84", "US1", "ZZZ", "000", "XXX"};
        for (String input : inputs) {
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(input), "Код: " + input);
        }
    }
}
