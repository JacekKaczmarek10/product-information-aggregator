package pl.kaczmarek.aggregator.service.aggregation;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class UpstreamInstrumentation {

    private final MeterRegistry meterRegistry;

    public UpstreamInstrumentation(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordCall(String service, String status, long startNanos) {
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

    public void recordShortCircuited(String serviceTag) {
        meterRegistry.counter(
                "aggregator.upstream.calls",
                "service", serviceTag,
                "status", "short_circuited"
        ).increment();
    }
}
