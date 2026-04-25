package pl.kaczmarek.aggregator.service.upstream;

import pl.kaczmarek.aggregator.model.upstream.PricingData;

public interface PricingClient {
    PricingData fetchPricing(String productId, String market, String customerId);
}
