package com.example.aggregator.service.upstream;

import com.example.aggregator.model.upstream.AvailabilityData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock Availability Service.
 * Typical latency: ~100ms  |  Reliability: 98%
 */
@Component
public class MockAvailabilityClient implements AvailabilityClient {

    private static final Logger log = LoggerFactory.getLogger(MockAvailabilityClient.class);
    private static final double FAILURE_RATE = 0.02; // 2%
    private static final int BASE_LATENCY_MS = 90;
    private static final int JITTER_MS = 20;

    // Closest warehouse per market
    private static final Map<String, String> MARKET_WAREHOUSE = Map.of(
            "nl-NL", "Amsterdam, NL",
            "de-DE", "Frankfurt, DE",
            "pl-PL", "Warsaw, PL",
            "en-GB", "London, GB",
            "sv-SE", "Stockholm, SE"
    );

    private static final Map<String, String> DELIVERY_DAYS = Map.of(
            "nl-NL", "1-2 business days",
            "de-DE", "1-2 business days",
            "pl-PL", "2-3 business days",
            "en-GB", "2-3 business days",
            "sv-SE", "3-4 business days"
    );

    @Override
    public AvailabilityData fetchAvailability(String productId, String market) {
        MockCatalogClient.simulateLatency(BASE_LATENCY_MS, JITTER_MS);
        MockCatalogClient.simulateFailure("AvailabilityService", FAILURE_RATE);

        log.debug("AvailabilityService: fetched availability product={} market={}", productId, market);

        // Stock level is semi-random but seeded to be stable for a given product+market
        int stockLevel = Math.abs((productId + market).hashCode() % 50) + 5;
        // Randomly simulate occasional out-of-stock
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            stockLevel = 0;
        }

        String warehouse = MARKET_WAREHOUSE.getOrDefault(market, "Central EU Warehouse");
        String delivery = DELIVERY_DAYS.getOrDefault(market, "3-5 business days");

        return new AvailabilityData(
                productId,
                stockLevel,
                warehouse,
                stockLevel > 0 ? delivery : "Out of stock — backorder available",
                stockLevel > 0
        );
    }
}
