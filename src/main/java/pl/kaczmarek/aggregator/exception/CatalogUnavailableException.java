package pl.kaczmarek.aggregator.exception;

public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String productId, Throwable cause) {
        super("Catalog unavailable for product: " + productId, cause);
    }
}
