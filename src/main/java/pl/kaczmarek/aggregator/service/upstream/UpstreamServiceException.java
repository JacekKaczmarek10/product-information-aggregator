package pl.kaczmarek.aggregator.service.upstream;

public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String serviceName, String message) {
        super(serviceName + ": " + message);
    }
}
