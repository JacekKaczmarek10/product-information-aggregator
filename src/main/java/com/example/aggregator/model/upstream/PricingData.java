package com.example.aggregator.model.upstream;

import java.math.BigDecimal;

public record PricingData(
        String productId,
        String market,
        String currency,
        BigDecimal basePrice,
        BigDecimal discountPercent,
        BigDecimal finalPrice,
        String priceValidUntil
) {}
