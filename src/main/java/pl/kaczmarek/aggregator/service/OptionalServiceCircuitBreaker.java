package pl.kaczmarek.aggregator.service;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

public class OptionalServiceCircuitBreaker {

    private final int failureThreshold;
    private final long openDurationMs;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openUntilEpochMs = 0;

    public OptionalServiceCircuitBreaker(int failureThreshold, long openDurationMs) {
        this(failureThreshold, openDurationMs, Clock.systemUTC());
    }

    OptionalServiceCircuitBreaker(int failureThreshold, long openDurationMs, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
        this.clock = clock;
    }

    public boolean isCallPermitted() {
        return clock.millis() >= openUntilEpochMs;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            openUntilEpochMs = clock.millis() + openDurationMs;
            consecutiveFailures.set(0);
        }
    }
}
