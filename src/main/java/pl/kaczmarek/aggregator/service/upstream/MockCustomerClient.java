package pl.kaczmarek.aggregator.service.upstream;

import pl.kaczmarek.aggregator.model.upstream.CustomerData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock Customer Service.
 * Typical latency: ~60ms  |  Reliability: 99%
 * Only called when a customerId is provided.
 */
@Component
@Slf4j
public class MockCustomerClient implements CustomerClient {
    private static final double FAILURE_RATE = 0.01;
    private static final int BASE_LATENCY_MS = 50;
    private static final int JITTER_MS = 20;

    @Override
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
