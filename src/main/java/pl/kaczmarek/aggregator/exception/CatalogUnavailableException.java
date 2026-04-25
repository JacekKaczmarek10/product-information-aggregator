package pl.kaczmarek.aggregator.exception;

public class CatalogUnavailableException extends RuntimeException {

    private final String productId;

    public CatalogUnavailableException(String productId, Throwable cause) {
        super("Catalog unavailable for product: " + productId, cause);
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }
}
