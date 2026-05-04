package pl.kaczmarek.aggregator.service.upstream;

import org.springframework.cache.annotation.Cacheable;
import pl.kaczmarek.aggregator.model.upstream.CatalogData;

public interface CatalogClient {

    @Cacheable(value = "catalog", key = "#productId + ':' + #market")
    CatalogData fetchProduct(String productId, String market);
}
