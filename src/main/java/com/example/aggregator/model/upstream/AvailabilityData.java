package com.example.aggregator.model.upstream;

public record AvailabilityData(
        String productId,
        int stockLevel,
        String warehouseLocation,
        String expectedDelivery,
        boolean inStock
) {}
