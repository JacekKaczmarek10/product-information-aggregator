package pl.kaczmarek.aggregator.service.upstream;

import pl.kaczmarek.aggregator.config.AggregatorConfig.AggregatorProperties;
import pl.kaczmarek.aggregator.config.AggregatorConfig.MarketSettings;
import pl.kaczmarek.aggregator.model.upstream.AvailabilityData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class MockAvailabilityClient implements AvailabilityClient {
    private static final double FAILURE_RATE = 0.02; // 2%
    private static final int BASE_LATENCY_MS = 90;
    private static final int JITTER_MS = 20;

    private final AggregatorProperties props;

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

        MarketSettings marketSettings = props.getMarkets().get(market);
        String warehouse = marketSettings != null ? marketSettings.getWarehouse() : "Central EU Warehouse";
        String delivery = marketSettings != null ? marketSettings.getDelivery() : "3-5 business days";

        return new AvailabilityData(
                productId,
                stockLevel,
                warehouse,
                stockLevel > 0 ? delivery : "Out of stock: backorder available",
                stockLevel > 0
        );
    }
}
