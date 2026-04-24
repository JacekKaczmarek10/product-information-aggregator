package com.example.aggregator.service.upstream;

import com.example.aggregator.model.upstream.CustomerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Mock Customer Service.
 * Typical latency: ~60ms  |  Reliability: 99%
 * Only called when a customerId is provided.
 */
@Component
public class MockCustomerClient {

    private static final Logger log = LoggerFactory.getLogger(MockCustomerClient.class);
    private static final double FAILURE_RATE = 0.01; // 1%
    private static final int BASE_LATENCY_MS = 50;
    private static final int JITTER_MS = 20;

    private static final Map<String, String> CUSTOMER_SEGMENTS = Map.of(
            "dealer", "DEALER",
            "workshop", "WORKSHOP",
            "enterprise", "ENTERPRISE"
    );

    public CustomerData fetchCustomer(String customerId) {
        MockCatalogClient.simulateLatency(BASE_LATENCY_MS, JITTER_MS);
        MockCatalogClient.simulateFailure("CustomerService", FAILURE_RATE);

        log.debug("CustomerService: fetched customer={}", customerId);

        // Deterministically derive segment from customer ID for stable mock behaviour
        int hash = Math.abs(customerId.hashCode());
        String[] segments = {"DEALER", "WORKSHOP", "ENTERPRISE", "RETAIL"};
        String segment = segments[hash % segments.length];
        boolean isPremium = (hash % 5) == 0;

        return new CustomerData(
                customerId,
                segment,
                List.of("Hydraulics", "Filters", "Electrical"),
                "en",
                isPremium
        );
    }
}
