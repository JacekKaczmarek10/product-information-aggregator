package com.example.aggregator.service.upstream;

/**
 * Thrown by mock upstream clients to simulate transient failures.
 */
public class UpstreamServiceException extends RuntimeException {

    private final String serviceName;

    public UpstreamServiceException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
