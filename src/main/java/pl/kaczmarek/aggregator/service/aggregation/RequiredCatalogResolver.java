package pl.kaczmarek.aggregator.service.aggregation;

import pl.kaczmarek.aggregator.config.AggregatorConfig.AggregatorProperties;
import pl.kaczmarek.aggregator.exception.CatalogUnavailableException;
import pl.kaczmarek.aggregator.exception.ProductNotFoundException;
import pl.kaczmarek.aggregator.model.upstream.CatalogData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequiredCatalogResolver {

    private final AggregatorProperties props;
    private final UpstreamInstrumentation instrumentation;

    public CompletableFuture<CatalogData> submit(Supplier<CatalogData> fetch, ExecutorService executor) {
        return CompletableFuture
                .supplyAsync(fetch, executor)
                .orTimeout(props.getCatalogTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    public CatalogData await(CompletableFuture<CatalogData> future, String productId) {
        long startNanos = System.nanoTime();
        try {
            CatalogData catalog = future.get(props.getCatalogTimeoutMs() + 10L, TimeUnit.MILLISECONDS);
            instrumentation.recordCall("catalog", "success", startNanos);
            return catalog;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw catalogUnavailable(productId, e, "interrupted", startNanos);
        } catch (TimeoutException e) {
            throw catalogUnavailable(productId, e, "timeout", startNanos);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ProductNotFoundException pnfe) {
                throw pnfe;
            }
            if (cause instanceof TimeoutException) {
                throw catalogUnavailable(productId, cause, "timeout", startNanos);
            }
            throw catalogUnavailable(productId, cause, "error", startNanos);
        }
    }

    private CatalogUnavailableException catalogUnavailable(
            String productId,
            Throwable cause,
            String metricStatus,
            long startNanos) {
        instrumentation.recordCall("catalog", metricStatus, startNanos);
        if ("timeout".equals(metricStatus)) {
            log.error("CatalogService timed out for product={}", productId);
        } else if ("error".equals(metricStatus)) {
            log.error("CatalogService failed for product={}: {}", productId, cause.getMessage());
        }
        return new CatalogUnavailableException(productId, cause);
    }
}
