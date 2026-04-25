package pl.kaczmarek.aggregator.service.upstream;

import pl.kaczmarek.aggregator.model.upstream.AvailabilityData;

public interface AvailabilityClient {
    AvailabilityData fetchAvailability(String productId, String market);
}
