package pl.kaczmarek.aggregator.service.aggregation;

import pl.kaczmarek.aggregator.config.AggregatorConfig.AggregatorProperties;
import pl.kaczmarek.aggregator.service.OptionalServiceCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
@Slf4j
public class OptionalUpstreamCoordinator {

    private final ExecutorService executor;
    private final UpstreamInstrumentation instrumentation;
    private final Map<String, OptionalServiceCircuitBreaker> breakers;

    public OptionalUpstreamCoordinator(
            ExecutorService executor,
            AggregatorProperties props,
            UpstreamInstrumentation instrumentation) {
        this.executor = executor;
        this.instrumentation = instrumentation;
        this.breakers = Map.of(
                "pricing", new OptionalServiceCircuitBreaker(props.getOptionalFailureThreshold(), props.getOptionalCircuitOpenMs()),
                "availability", new OptionalServiceCircuitBreaker(props.getOptionalFailureThreshold(), props.getOptionalCircuitOpenMs()),
                "customer", new OptionalServiceCircuitBreaker(props.getOptionalFailureThreshold(), props.getOptionalCircuitOpenMs())
        );
    }

    public <T> CompletableFuture<T> submitOptional(
            String serviceTag,
            Supplier<T> supplier,
            int timeoutMs,
            String productId) {
        OptionalServiceCircuitBreaker breaker = breakers.get(serviceTag);
        if (!breaker.isCallPermitted()) {
            log.warn("Skipping {} call for product={} due to open circuit", serviceTag, productId);
            instrumentation.recordShortCircuited(serviceTag);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture
                .supplyAsync(supplier, executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public <T> Optional<T> awaitOptional(
            CompletableFuture<T> future,
            String serviceName,
            String serviceTag,
            String productId) {
        long startNanos = System.nanoTime();
        try {
            Optional<T> result = Optional.ofNullable(future.get());
            instrumentation.recordCall(serviceTag, "success", startNanos);
            breakers.get(serviceTag).recordSuccess();
            return result;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                return optionalFailed(future, serviceName, serviceTag, productId, cause, "timeout", startNanos);
            }
            return optionalFailed(future, serviceName, serviceTag, productId, cause, "error", startNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            instrumentation.recordCall(serviceTag, "interrupted", startNanos);
            return Optional.empty();
        }
    }

    private <T> Optional<T> optionalFailed(
            CompletableFuture<T> future,
            String serviceName,
            String serviceTag,
            String productId,
            Throwable cause,
            String metricStatus,
            long startNanos) {
        instrumentation.recordCall(serviceTag, metricStatus, startNanos);
        if ("timeout".equals(metricStatus)) {
            log.warn("{} timed out for product={}", serviceName, productId);
            future.cancel(true);
        } else {
            log.warn("{} failed for product={}: {}", serviceName, productId, cause.getMessage());
        }
        breakers.get(serviceTag).recordFailure();
        return Optional.empty();
    }
}
