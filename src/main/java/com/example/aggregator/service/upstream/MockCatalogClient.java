package com.example.aggregator.service.upstream;

import com.example.aggregator.model.upstream.CatalogData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock Catalog Service.
 * Typical latency: ~50ms  |  Reliability: 99.9%
 */
@Component
public class MockCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(MockCatalogClient.class);
    private static final double FAILURE_RATE = 0.001; // 0.1%
    private static final int BASE_LATENCY_MS = 40;
    private static final int JITTER_MS = 20;

    private static final Map<String, Map<String, String>> LOCALIZED_NAMES = Map.of(
            "nl-NL", Map.of(
                    "PROD-001", "Hydraulisch Pompsysteem",
                    "PROD-002", "Dieselfilter Universeel"
            ),
            "de-DE", Map.of(
                    "PROD-001", "Hydraulikpumpensystem",
                    "PROD-002", "Dieselfilter Universal"
            ),
            "pl-PL", Map.of(
                    "PROD-001", "Układ pompy hydraulicznej",
                    "PROD-002", "Filtr dieselowy uniwersalny"
            )
    );

    public CatalogData fetchProduct(String productId, String market) {
        simulateLatency(BASE_LATENCY_MS, JITTER_MS);
        simulateFailure("CatalogService", FAILURE_RATE);

        log.debug("CatalogService: fetched product={} market={}", productId, market);

        String lang = market.contains("-") ? market.split("-")[0] : "en";
        Map<String, String> localizedNamesForMarket = LOCALIZED_NAMES.getOrDefault(market, Map.of());
        String localizedName = localizedNamesForMarket.getOrDefault(productId,
                "Hydraulic Pump System " + productId);

        return new CatalogData(
                productId,
                "Hydraulic Pump System",
                "High-performance hydraulic pump for agricultural machinery.",
                "Hydraulics",
                "AgroTech",
                Map.of(
                        "weight_kg", "4.2",
                        "pressure_bar", "250",
                        "flow_lpm", "28",
                        "port_size", "3/4\" BSP"
                ),
                List.of(
                        "https://cdn.example.com/products/" + productId + "/front.jpg",
                        "https://cdn.example.com/products/" + productId + "/side.jpg"
                ),
                localizedName,
                "High-performance hydraulic pump for agricultural and construction machinery."
        );
    }

    static void simulateLatency(int baseMs, int jitterMs) {
        try {
            long sleep = baseMs + ThreadLocalRandom.current().nextLong(jitterMs);
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void simulateFailure(String service, double failureRate) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new UpstreamServiceException(service, service + " simulated transient failure");
        }
    }
}
