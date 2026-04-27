package pl.kaczmarek.aggregator.service.upstream;

import pl.kaczmarek.aggregator.config.AggregatorConfig.AggregatorProperties;
import pl.kaczmarek.aggregator.config.AggregatorConfig.MarketSettings;
import pl.kaczmarek.aggregator.model.upstream.PricingData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MockPricingClient implements PricingClient {
    private static final double FAILURE_RATE = 0.005; // 0.5%
    private static final int BASE_LATENCY_MS = 70;
    private static final int JITTER_MS = 20;

    private static final Map<String, BigDecimal> BASE_PRICES_EUR = Map.of(
            "PROD-001", new BigDecimal("349.99"),
            "PROD-002", new BigDecimal("24.50")
    );

    private final AggregatorProperties props;

    @Override
    public PricingData fetchPricing(String productId, String market, String customerId) {
        MockCatalogClient.simulateLatency(BASE_LATENCY_MS, JITTER_MS);
        MockCatalogClient.simulateFailure("PricingService", FAILURE_RATE);

        log.debug("PricingService: fetched pricing product={} market={} customer={}", productId, market, customerId);

        MarketSettings marketSettings = props.getMarkets().get(market);
        String currency = marketSettings != null ? marketSettings.getCurrency() : "EUR";
        BigDecimal fx = marketSettings != null
                ? BigDecimal.valueOf(marketSettings.getFx())
                : BigDecimal.ONE;
        BigDecimal baseEur = BASE_PRICES_EUR.getOrDefault(productId, new BigDecimal("199.99"));
        BigDecimal basePrice = baseEur.multiply(fx).setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountPct = resolveDiscountPercent(customerId);

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

    private BigDecimal resolveDiscountPercent(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return BigDecimal.ZERO;
        }
        int hash = Math.abs(customerId.hashCode());
        String[] segments = {"DEALER", "WORKSHOP", "ENTERPRISE", "RETAIL"};
        String segment = segments[hash % segments.length];
        boolean premium = (hash % 5) == 0;

        BigDecimal baseDiscount = switch (segment) {
            case "DEALER" -> new BigDecimal("8");
            case "WORKSHOP" -> new BigDecimal("5");
            case "ENTERPRISE" -> new BigDecimal("12");
            default -> new BigDecimal("2");
        };
        if (premium) {
            baseDiscount = baseDiscount.add(new BigDecimal("3"));
        }
        return baseDiscount.min(new BigDecimal("20"));
    }
}
