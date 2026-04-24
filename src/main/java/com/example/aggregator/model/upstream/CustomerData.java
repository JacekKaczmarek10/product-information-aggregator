package com.example.aggregator.model.upstream;

import java.util.List;

public record CustomerData(
        String customerId,
        String segment,
        List<String> preferredCategories,
        String preferredLanguage,
        boolean isPremium
) {}
