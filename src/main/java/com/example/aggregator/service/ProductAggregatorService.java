package com.example.aggregator.service;

import com.example.aggregator.config.AggregatorConfig.AggregatorProperties;
import com.example.aggregator.exception.CatalogUnavailableException;
import com.example.aggregator.model.response.ProductResponse;
import com.example.aggregator.model.response.ProductResponse.*;
import com.example.aggregator.model.upstream.*;
import com.example.aggregator.service.upstream.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Supplier;

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

    private final CatalogClient catalogClient;
    private final PricingClient pricingClient;
    private final AvailabilityClient availabilityClient;
    private final CustomerClient customerClient;
    private final ExecutorService executor;
    private final AggregatorProperties props;
    private final MeterRegistry meterRegistry;
    private final Map<String, OptionalServiceCircuitBreaker> optionalBreakers;

    public ProductAggregatorService(
            CatalogClient catalogClient,
            PricingClient pricingClient,
            AvailabilityClient availabilityClient,
            CustomerClient customerClient,
            ExecutorService executor,
            AggregatorProperties props,
            MeterRegistry meterRegistry) {
        this.catalogClient = catalogClient;
        this.pricingClient = pricingClient;
        this.availabilityClient = availabilityClient;
        this.customerClient = customerClient;
        this.executor = executor;
        this.props = props;
        this.meterRegistry = meterRegistry;
        this.optionalBreakers = Map.of(
                "pricing", new OptionalServiceCircuitBreaker(props.getOptionalFailureThreshold(), props.getOptionalCircuitOpenMs()),
                "availability", new OptionalServiceCircuitBreaker(props.getOptionalFailureThreshold(), props.getOptionalCircuitOpenMs()),
                "customer", new OptionalServiceCircuitBreaker(props.getOptionalFailureThreshold(), props.getOptionalCircuitOpenMs())
        );
    }

    public ProductResponse aggregate(String productId, String market, String customerId) {
        String language = extractLanguage(market);

        // --- Fire all calls concurrently ---
        CompletableFuture<CatalogData> catalogFuture = CompletableFuture
                .supplyAsync(() -> catalogClient.fetchProduct(productId, market), executor)
                .orTimeout(props.getCatalogTimeoutMs(), TimeUnit.MILLISECONDS);

        CompletableFuture<PricingData> pricingFuture = callOptional(
                "pricing",
                () -> pricingClient.fetchPricing(productId, market, customerId),
                props.getPricingTimeoutMs(),
                productId
        );

        CompletableFuture<AvailabilityData> availabilityFuture = callOptional(
                "availability",
                () -> availabilityClient.fetchAvailability(productId, market),
                props.getAvailabilityTimeoutMs(),
                productId
        );

        // Customer call is only made when a customerId is present
        CompletableFuture<CustomerData> customerFuture = (customerId != null && !customerId.isBlank())
                ? callOptional(
                        "customer",
                        () -> customerClient.fetchCustomer(customerId),
                        props.getCustomerTimeoutMs(),
                        productId
                )
                : CompletableFuture.completedFuture(null);

        // --- Resolve catalog (required) ---
        CatalogData catalog;
        long catalogStartNanos = System.nanoTime();
        try {
            catalog = catalogFuture.get(props.getCatalogTimeoutMs() + 10L, TimeUnit.MILLISECONDS);
            recordUpstreamMetrics("catalog", "success", catalogStartNanos);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                recordUpstreamMetrics("catalog", "timeout", catalogStartNanos);
                log.error("CatalogService timed out for product={}", productId);
                throw new CatalogUnavailableException(productId, cause);
            }
            recordUpstreamMetrics("catalog", "error", catalogStartNanos);
            log.error("CatalogService failed for product={}: {}", productId, e.getCause().getMessage());
            throw new CatalogUnavailableException(productId, cause);
        } catch (TimeoutException e) {
            recordUpstreamMetrics("catalog", "timeout", catalogStartNanos);
            log.error("CatalogService timed out for product={}", productId);
            throw new CatalogUnavailableException(productId, e);
        } catch (InterruptedException e) {
            recordUpstreamMetrics("catalog", "interrupted", catalogStartNanos);
            Thread.currentThread().interrupt();
            throw new CatalogUnavailableException(productId, e);
        }

        // --- Resolve optional services (best-effort) ---
        Optional<PricingData> pricing = resolveOptional(pricingFuture, "PricingService", "pricing", productId);
        Optional<AvailabilityData> availability = resolveOptional(availabilityFuture, "AvailabilityService", "availability", productId);
        Optional<CustomerData> customer = resolveOptional(customerFuture, "CustomerService", "customer", productId);

        return buildResponse(productId, market, language, catalog, pricing, availability, customer);
    }

    private <T> Optional<T> resolveOptional(CompletableFuture<T> future, String serviceName, String serviceTag, String productId) {
        long startNanos = System.nanoTime();
        try {
            Optional<T> result = Optional.ofNullable(future.get());
            recordUpstreamMetrics(serviceTag, "success", startNanos);
            optionalBreakers.get(serviceTag).recordSuccess();
            return result;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                recordUpstreamMetrics(serviceTag, "timeout", startNanos);
                log.warn("{} timed out for product={}", serviceName, productId);
                future.cancel(true);
                optionalBreakers.get(serviceTag).recordFailure();
                return Optional.empty();
            }
            recordUpstreamMetrics(serviceTag, "error", startNanos);
            log.warn("{} failed for product={}: {}", serviceName, productId, cause.getMessage());
            optionalBreakers.get(serviceTag).recordFailure();
            return Optional.empty();
        } catch (InterruptedException e) {
            recordUpstreamMetrics(serviceTag, "interrupted", startNanos);
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

    private <T> CompletableFuture<T> callOptional(
            String serviceTag,
            Supplier<T> supplier,
            int timeoutMs,
            String productId) {
        OptionalServiceCircuitBreaker breaker = optionalBreakers.get(serviceTag);
        if (!breaker.isCallPermitted()) {
            log.warn("Skipping {} call for product={} due to open circuit", serviceTag, productId);
            meterRegistry.counter(
                    "aggregator.upstream.calls",
                    "service", serviceTag,
                    "status", "short_circuited"
            ).increment();
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture
                .supplyAsync(supplier, executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private void recordUpstreamMetrics(String service, String status, long startNanos) {
        long durationNanos = System.nanoTime() - startNanos;
        meterRegistry.timer(
                "aggregator.upstream.latency",
                "service", service,
                "status", status
        ).record(durationNanos, TimeUnit.NANOSECONDS);
        meterRegistry.counter(
                "aggregator.upstream.calls",
                "service", service,
                "status", status
        ).increment();
    }
}
