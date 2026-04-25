package pl.kaczmarek.aggregator.service.upstream;

import pl.kaczmarek.aggregator.model.upstream.CustomerData;

public interface CustomerClient {
    CustomerData fetchCustomer(String customerId);
}
