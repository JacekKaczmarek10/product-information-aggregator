package pl.kaczmarek.aggregator.service.upstream;

import pl.kaczmarek.aggregator.model.upstream.CatalogData;

public interface CatalogClient {
    CatalogData fetchProduct(String productId, String market);
}
