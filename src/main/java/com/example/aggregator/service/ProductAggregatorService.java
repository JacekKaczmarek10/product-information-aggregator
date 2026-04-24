package com.example.aggregator.service;

import com.example.aggregator.config.AggregatorConfig.AggregatorProperties;
import com.example.aggregator.exception.CatalogUnavailableException;
import com.example.aggregator.model.response.ProductResponse;
import com.example.aggregator.model.response.ProductResponse.*;
import com.example.aggregator.model.upstream.*;
import com.example.aggregator.service.upstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.*;

/**
 * Core aggregation logic.
 *
 * Strategy:
 *  - All upstream calls are fired concurrently.
 *  - Catalog is REQUIRED: if it fails or times out, the whole request fails with 503.
 *  - Pricing, Availability, Customer are OPTIONAL: failures yield a degraded but valid response.
 *  - Per-service timeouts guard against slow upstreams; the executor provides thread isolation
 *    so a stuck upstream doesn't exhaust the request-handling threads.
 */
@Service
public class ProductAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(ProductAggregatorService.class);

    private final MockCatalogClient catalogClient;
    private final MockPricingClient pricingClient;
    private final MockAvailabilityClient availabilityClient;
    private final MockCustomerClient customerClient;
    private final ExecutorService executor;
    private final AggregatorProperties props;

    public ProductAggregatorService(
            MockCatalogClient catalogClient,
            MockPricingClient pricingClient,
            MockAvailabilityClient availabilityClient,
            MockCustomerClient customerClient,
            ExecutorService executor,
            AggregatorProperties props) {
        this.catalogClient = catalogClient;
        this.pricingClient = pricingClient;
        this.availabilityClient = availabilityClient;
        this.customerClient = customerClient;
        this.executor = executor;
        this.props = props;
    }

    public ProductResponse aggregate(String productId, String market, String customerId) {
        String language = extractLanguage(market);

        // --- Fire all calls concurrently ---
        CompletableFuture<CatalogData> catalogFuture = CompletableFuture
                .supplyAsync(() -> catalogClient.fetchProduct(productId, market), executor)
                .orTimeout(props.getCatalogTimeoutMs(), TimeUnit.MILLISECONDS);

        CompletableFuture<PricingData> pricingFuture = CompletableFuture
                .supplyAsync(() -> pricingClient.fetchPricing(productId, market, customerId), executor)
                .orTimeout(props.getPricingTimeoutMs(), TimeUnit.MILLISECONDS);

        CompletableFuture<AvailabilityData> availabilityFuture = CompletableFuture
                .supplyAsync(() -> availabilityClient.fetchAvailability(productId, market), executor)
                .orTimeout(props.getAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS);

        // Customer call is only made when a customerId is present
        CompletableFuture<CustomerData> customerFuture = (customerId != null && !customerId.isBlank())
                ? CompletableFuture
                        .supplyAsync(() -> customerClient.fetchCustomer(customerId), executor)
                        .orTimeout(props.getCustomerTimeoutMs(), TimeUnit.MILLISECONDS)
                : CompletableFuture.completedFuture(null);

        // --- Resolve catalog (required) ---
        CatalogData catalog;
        try {
            catalog = catalogFuture.get(props.getCatalogTimeoutMs() + 10L, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            log.error("CatalogService failed for product={}: {}", productId, e.getCause().getMessage());
            throw new CatalogUnavailableException(productId, e.getCause());
        } catch (TimeoutException e) {
            log.error("CatalogService timed out for product={}", productId);
            throw new CatalogUnavailableException(productId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogUnavailableException(productId, e);
        }

        // --- Resolve optional services (best-effort) ---
        Optional<PricingData> pricing = resolveOptional(pricingFuture, "PricingService", productId);
        Optional<AvailabilityData> availability = resolveOptional(availabilityFuture, "AvailabilityService", productId);
        Optional<CustomerData> customer = resolveOptional(customerFuture, "CustomerService", productId);

        return buildResponse(productId, market, language, catalog, pricing, availability, customer);
    }

    private <T> Optional<T> resolveOptional(CompletableFuture<T> future, String serviceName, String productId) {
        try {
            return Optional.ofNullable(future.get());
        } catch (ExecutionException e) {
            log.warn("{} failed for product={}: {}", serviceName, productId, e.getCause().getMessage());
            return Optional.empty();
        } catch (TimeoutException e) {
            log.warn("{} timed out for product={}", serviceName, productId);
            future.cancel(true);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private ProductResponse buildResponse(
            String productId,
            String market,
            String language,
            CatalogData catalog,
            Optional<PricingData> pricingOpt,
            Optional<AvailabilityData> availabilityOpt,
            Optional<CustomerData> customerOpt) {

        PricingInfo pricingInfo = pricingOpt.map(p -> new PricingInfo(
                p.currency(), p.basePrice(), p.discountPercent(), p.finalPrice(), p.priceValidUntil()
        )).orElse(null);

        AvailabilityInfo availabilityInfo = availabilityOpt.map(a -> new AvailabilityInfo(
                a.inStock(), a.stockLevel(), a.warehouseLocation(), a.expectedDelivery()
        )).orElse(null);

        PersonalizationInfo personalizationInfo = customerOpt.map(c -> new PersonalizationInfo(
                c.segment(), c.isPremium(), c.preferredCategories()
        )).orElse(null);

        DataStatus status = new DataStatus(
                true,
                pricingOpt.isPresent(),
                availabilityOpt.isPresent(),
                customerOpt.isPresent()
        );

        return new ProductResponse(
                productId,
                market,
                language,
                catalog.localizedName(),
                catalog.localizedDescription(),
                catalog.category(),
                catalog.brand(),
                catalog.specifications(),
                catalog.imageUrls(),
                pricingInfo,
                availabilityInfo,
                personalizationInfo,
                status
        );
    }

    private String extractLanguage(String market) {
        // "nl-NL" -> "nl", "de-DE" -> "de", etc.
        return market.contains("-") ? market.split("-")[0] : market;
    }
}
