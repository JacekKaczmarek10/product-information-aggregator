package pl.kaczmarek.aggregator.service.upstream;

import lombok.Getter;

/**
 * Thrown by mock upstream clients to simulate transient failures.
 */
@Getter
public class UpstreamServiceException extends RuntimeException {

    private final String serviceName;

    public UpstreamServiceException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }
}
