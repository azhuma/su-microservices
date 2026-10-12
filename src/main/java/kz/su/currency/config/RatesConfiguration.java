package kz.su.currency.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import kz.su.currency.service.CurrencyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RatesConfiguration {
    @Bean
    CurrencyResolver currencyResolver() {
        return new CurrencyResolver();
    }

    @Bean
    Clock clock(@Value("${rates.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    RestClient ratesRestClient(RestClient.Builder builder,
                              @Value("${rates.provider-url}") String url,
                              @Value("${rates.connect-timeout}") Duration connectTimeout,
                              @Value("${rates.read-timeout}") Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return builder.baseUrl(url).requestFactory(factory).build();
    }
}
