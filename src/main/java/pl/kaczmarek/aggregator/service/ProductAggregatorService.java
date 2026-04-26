package pl.kaczmarek.aggregator.service;

import pl.kaczmarek.aggregator.config.AggregatorConfig.AggregatorProperties;
import pl.kaczmarek.aggregator.model.response.ProductResponse;
import pl.kaczmarek.aggregator.model.upstream.AvailabilityData;
import pl.kaczmarek.aggregator.model.upstream.CatalogData;
import pl.kaczmarek.aggregator.model.upstream.CustomerData;
import pl.kaczmarek.aggregator.model.upstream.PricingData;
import pl.kaczmarek.aggregator.service.aggregation.OptionalUpstreamCoordinator;
import pl.kaczmarek.aggregator.service.aggregation.ProductResponseMapper;
import pl.kaczmarek.aggregator.service.aggregation.RequiredCatalogResolver;
import pl.kaczmarek.aggregator.service.upstream.AvailabilityClient;
import pl.kaczmarek.aggregator.service.upstream.CatalogClient;
import pl.kaczmarek.aggregator.service.upstream.CustomerClient;
import pl.kaczmarek.aggregator.service.upstream.PricingClient;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates concurrent upstream calls and delegates result handling to focused collaborators.
 */
@Service
public class ProductAggregatorService {

    private final CatalogClient catalogClient;
    private final PricingClient pricingClient;
    private final AvailabilityClient availabilityClient;
    private final CustomerClient customerClient;
    private final ExecutorService executor;
    private final AggregatorProperties props;
    private final RequiredCatalogResolver catalogResolver;
    private final OptionalUpstreamCoordinator optionalUpstream;
    private final ProductResponseMapper responseMapper;

    public ProductAggregatorService(
            CatalogClient catalogClient,
            PricingClient pricingClient,
            AvailabilityClient availabilityClient,
            CustomerClient customerClient,
            ExecutorService executor,
            AggregatorProperties props,
            RequiredCatalogResolver catalogResolver,
            OptionalUpstreamCoordinator optionalUpstream,
            ProductResponseMapper responseMapper) {
        this.catalogClient = catalogClient;
        this.pricingClient = pricingClient;
        this.availabilityClient = availabilityClient;
        this.customerClient = customerClient;
        this.executor = executor;
        this.props = props;
        this.catalogResolver = catalogResolver;
        this.optionalUpstream = optionalUpstream;
        this.responseMapper = responseMapper;
    }

    public ProductResponse aggregate(String productId, String market, String customerId) {
        String language = responseMapper.extractLanguage(market);

        CompletableFuture<CatalogData> catalogFuture = catalogResolver.submit(
                () -> catalogClient.fetchProduct(productId, market),
                executor
        );

        CompletableFuture<PricingData> pricingFuture = optionalUpstream.submitOptional(
                "pricing",
                () -> pricingClient.fetchPricing(productId, market, customerId),
                props.getPricingTimeoutMs(),
                productId
        );

        CompletableFuture<AvailabilityData> availabilityFuture = optionalUpstream.submitOptional(
                "availability",
                () -> availabilityClient.fetchAvailability(productId, market),
                props.getAvailabilityTimeoutMs(),
                productId
        );

        CompletableFuture<CustomerData> customerFuture = (customerId != null && !customerId.isBlank())
                ? optionalUpstream.submitOptional(
                        "customer",
                        () -> customerClient.fetchCustomer(customerId),
                        props.getCustomerTimeoutMs(),
                        productId
                )
                : CompletableFuture.completedFuture(null);

        CatalogData catalog = catalogResolver.await(catalogFuture, productId);

        Optional<PricingData> pricing = optionalUpstream.awaitOptional(pricingFuture, "PricingService", "pricing", productId);
        Optional<AvailabilityData> availability = optionalUpstream.awaitOptional(
                availabilityFuture, "AvailabilityService", "availability", productId);
        Optional<CustomerData> customer = optionalUpstream.awaitOptional(
                customerFuture, "CustomerService", "customer", productId);

        return responseMapper.toResponse(productId, market, language, catalog, pricing, availability, customer);
    }
}
