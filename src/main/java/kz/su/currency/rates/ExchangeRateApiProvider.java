package kz.su.currency.rates;

import java.time.LocalDate;
import java.util.List;
import kz.su.currency.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExchangeRateApiProvider implements RatesProvider {
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateApiProvider.class);
    private final RestClient client;
    private final ExchangeRateApiParser parser = new ExchangeRateApiParser();

    public ExchangeRateApiProvider(RestClient ratesRestClient) {
        this.client = ratesRestClient;
    }

    @Override
    public List<ExchangeRate> fetch(LocalDate date) {
        try {
            byte[] json = client.get().retrieve().body(byte[].class);
            return parser.parse(json, date);
        } catch (Exception exception) {
            log.warn("Could not load ExchangeRate-API rates: {}", exception.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE",
                    "Exchange rate provider is unavailable or returned invalid data");
        }
    }
}
