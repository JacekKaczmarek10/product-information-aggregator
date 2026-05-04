package pl.kaczmarek.aggregator.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports per-upstream health based on recorded Micrometer counters.
 * A service is considered degraded when its error rate in the last sample
 * exceeds the configured threshold (default 20%).
 */
@Component("upstreamServices")
public class UpstreamHealthIndicator implements HealthIndicator {

    private static final List<String> SERVICES = List.of("catalog", "pricing", "availability", "customer");
    private static final double DEGRADED_THRESHOLD = 0.20;

    private final MeterRegistry meterRegistry;

    public UpstreamHealthIndicator(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean anyDegraded = false;

        for (String service : SERVICES) {
            double total = countFor(service, null);
            double errors = countFor(service, "error") + countFor(service, "timeout");
            double shortCircuited = countFor(service, "short_circuited");

            Map<String, Object> serviceDetails = new LinkedHashMap<>();
            serviceDetails.put("total_calls", (long) total);
            serviceDetails.put("errors", (long) errors);
            serviceDetails.put("short_circuited", (long) shortCircuited);

            String status;
            if (total == 0) {
                status = "NO_DATA";
            } else if (errors / total > DEGRADED_THRESHOLD) {
                status = "DEGRADED";
                anyDegraded = true;
            } else {
                status = "UP";
            }
            serviceDetails.put("status", status);
            details.put(service, serviceDetails);
        }

        Health.Builder builder = anyDegraded ? Health.down() : Health.up();
        return builder.withDetails(details).build();
    }

    private double countFor(String service, String status) {
        Search search = Search.in(meterRegistry)
                .name("aggregator.upstream.calls")
                .tag("service", service);
        if (status != null) {
            search = search.tag("status", status);
        }
        return search.counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }
}
