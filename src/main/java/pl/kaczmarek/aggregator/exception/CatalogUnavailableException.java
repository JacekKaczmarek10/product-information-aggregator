package pl.kaczmarek.aggregator.exception;

import lombok.Getter;

@Getter
public class CatalogUnavailableException extends RuntimeException {

    private final String productId;

    public CatalogUnavailableException(String productId, Throwable cause) {
        super("Catalog unavailable for product: " + productId, cause);
        this.productId = productId;
    }
}
