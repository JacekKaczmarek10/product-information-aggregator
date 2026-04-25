package com.example.aggregator.service.upstream;

import com.example.aggregator.model.upstream.PricingData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * Mock Pricing Service.
 * Typical latency: ~80ms  |  Reliability: 99.5%
 */
@Component
public class MockPricingClient implements PricingClient {

    private static final Logger log = LoggerFactory.getLogger(MockPricingClient.class);
    private static final double FAILURE_RATE = 0.005; // 0.5%
    private static final int BASE_LATENCY_MS = 70;
    private static final int JITTER_MS = 20;

    private static final Map<String, String> MARKET_CURRENCY = Map.of(
            "nl-NL", "EUR",
            "de-DE", "EUR",
            "pl-PL", "PLN",
            "en-GB", "GBP",
            "sv-SE", "SEK"
    );

    private static final Map<String, BigDecimal> BASE_PRICES_EUR = Map.of(
            "PROD-001", new BigDecimal("349.99"),
            "PROD-002", new BigDecimal("24.50")
    );

    private static final Map<String, BigDecimal> FX = Map.of(
            "EUR", BigDecimal.ONE,
            "PLN", new BigDecimal("4.25"),
            "GBP", new BigDecimal("0.86"),
            "SEK", new BigDecimal("11.40")
    );

    @Override
    public PricingData fetchPricing(String productId, String market, String customerId) {
        MockCatalogClient.simulateLatency(BASE_LATENCY_MS, JITTER_MS);
        MockCatalogClient.simulateFailure("PricingService", FAILURE_RATE);

        log.debug("PricingService: fetched pricing product={} market={} customer={}", productId, market, customerId);

        String currency = MARKET_CURRENCY.getOrDefault(market, "EUR");
        BigDecimal fx = FX.getOrDefault(currency, BigDecimal.ONE);
        BigDecimal baseEur = BASE_PRICES_EUR.getOrDefault(productId, new BigDecimal("199.99"));
        BigDecimal basePrice = baseEur.multiply(fx).setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountPct = customerId != null
                ? BigDecimal.valueOf(Math.abs(customerId.hashCode() % 16))
                : BigDecimal.ZERO;

        BigDecimal finalPrice = basePrice
                .multiply(BigDecimal.ONE.subtract(discountPct.divide(new BigDecimal("100"))))
                .setScale(2, RoundingMode.HALF_UP);

        return new PricingData(
                productId,
                market,
                currency,
                basePrice,
                discountPct,
                finalPrice,
                LocalDate.now().plusDays(1).toString()
        );
    }
}
