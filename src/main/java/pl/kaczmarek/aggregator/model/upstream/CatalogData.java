package pl.kaczmarek.aggregator.model.upstream;

import java.util.List;
import java.util.Map;

public record CatalogData(
        String productId,
        String name,
        String description,
        String category,
        String brand,
        Map<String, String> specifications,
        List<String> imageUrls,
        String localizedName,
        String localizedDescription
) {}
