package kz.su.currency.rates;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

public class ExchangeRateApiParser {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public List<ExchangeRate> parse(byte[] json, LocalDate fetchedOn) {
        if (json == null || json.length == 0) {
            throw new IllegalArgumentException("Empty provider response");
        }
        JsonNode snapshot;
        try {
            snapshot = mapper.readTree(json);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid provider JSON", exception);
        }
        if (snapshot == null || !snapshot.isObject()
                || !"success".equals(snapshot.path("result").textValue())
                || !"USD".equals(snapshot.path("base_code").textValue())) {
            throw new IllegalArgumentException("Expected successful USD snapshot");
        }
        LocalDate rateDate = publicationDate(snapshot.path("time_last_update_unix"));
        if (rateDate.isAfter(fetchedOn)) {
            throw new IllegalArgumentException("Provider returned future rates");
        }
        JsonNode quotes = snapshot.path("rates");
        if (!quotes.isObject() || quotes.isEmpty()
                || positiveRate(quotes.path("USD")).compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Expected USD base rate of one");
        }
        BigDecimal kztPerUsd = positiveRate(quotes.path("KZT"));
        var rates = new ArrayList<ExchangeRate>();
        var fields = quotes.properties().iterator();
        while (fields.hasNext()) {
            var field = fields.next();
            String code = field.getKey();
            if (!code.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Invalid currency code");
            }
            BigDecimal unitsPerUsd = positiveRate(field.getValue());
            Currency currency = supportedCurrency(code);
            if (currency == null) {
                // The provider also publishes codes absent from the JDK ISO 4217 catalogue.
                continue;
            }
            // Store KZT per unit: (KZT/USD) / (currency/USD).
            BigDecimal kztPerUnit = kztPerUsd.divide(unitsPerUsd, 18, RoundingMode.HALF_UP);
            if (kztPerUnit.signum() <= 0 || kztPerUnit.precision() - kztPerUnit.scale() > 20) {
                throw new IllegalArgumentException("Rate does not fit database precision");
            }
            rates.add(new ExchangeRate(code, currency.getNumericCodeAsString(), kztPerUnit, rateDate, fetchedOn));
        }
        return rates;
    }

    private BigDecimal positiveRate(JsonNode value) {
        if (!value.isNumber() || value.decimalValue().signum() <= 0) {
            throw new IllegalArgumentException("Expected positive numeric rate");
        }
        return value.decimalValue();
    }

    private LocalDate publicationDate(JsonNode timestamp) {
        if (!timestamp.isIntegralNumber() || !timestamp.canConvertToLong() || timestamp.longValue() <= 0) {
            throw new IllegalArgumentException("Invalid publication timestamp");
        }
        try {
            return Instant.ofEpochSecond(timestamp.longValue()).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid publication timestamp", exception);
        }
    }

    private Currency supportedCurrency(String code) {
        try {
            Currency currency = Currency.getInstance(code);
            return currency.getDefaultFractionDigits() >= 0 ? currency : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
