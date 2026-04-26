package pl.kaczmarek.aggregator.service;

import pl.kaczmarek.aggregator.config.AggregatorConfig.AggregatorProperties;
import pl.kaczmarek.aggregator.exception.CatalogUnavailableException;
import pl.kaczmarek.aggregator.model.response.ProductResponse;
import pl.kaczmarek.aggregator.service.upstream.MockAvailabilityClient;
import pl.kaczmarek.aggregator.service.upstream.MockCatalogClient;
import pl.kaczmarek.aggregator.service.upstream.MockCustomerClient;
import pl.kaczmarek.aggregator.service.upstream.MockPricingClient;
import pl.kaczmarek.aggregator.service.aggregation.OptionalUpstreamCoordinator;
import pl.kaczmarek.aggregator.service.aggregation.ProductResponseMapper;
import pl.kaczmarek.aggregator.service.aggregation.RequiredCatalogResolver;
import pl.kaczmarek.aggregator.service.aggregation.UpstreamInstrumentation;
import pl.kaczmarek.aggregator.service.upstream.UpstreamServiceException;
import pl.kaczmarek.aggregator.testsupport.UpstreamTestFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductAggregatorServiceTest {

    @Mock
    MockCatalogClient catalogClient;
    @Mock
    MockPricingClient pricingClient;
    @Mock
    MockAvailabilityClient availabilityClient;
    @Mock
    MockCustomerClient customerClient;

    ProductAggregatorService service;

    @BeforeEach
    void setUp() {
        AggregatorProperties props = new AggregatorProperties();
        props.setCatalogTimeoutMs(500);
        props.setPricingTimeoutMs(500);
        props.setAvailabilityTimeoutMs(500);
        props.setCustomerTimeoutMs(500);
        service = createService(props);
    }

    private ProductAggregatorService createService(AggregatorProperties props) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UpstreamInstrumentation instrumentation = new UpstreamInstrumentation(registry);
        ExecutorService executor = Executors.newCachedThreadPool();
        return new ProductAggregatorService(
                catalogClient, pricingClient, availabilityClient, customerClient,
                executor, props,
                new RequiredCatalogResolver(props, instrumentation),
                new OptionalUpstreamCoordinator(executor, props, instrumentation),
                new ProductResponseMapper()
        );
    }

    @Test
    void fullResponse_whenAllServicesSucceed() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(UpstreamTestFixtures.defaultPricing());
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(UpstreamTestFixtures.defaultAvailability());
        when(customerClient.fetchCustomer(any())).thenReturn(UpstreamTestFixtures.defaultCustomer());

        ProductResponse response = service.aggregate("PROD-001", "de-DE", "CUST-1");

        assertThat(response.dataStatus().catalogOk()).isTrue();
        assertThat(response.dataStatus().pricingAvailable()).isTrue();
        assertThat(response.dataStatus().availabilityKnown()).isTrue();
        assertThat(response.dataStatus().personalized()).isTrue();
        assertThat(response.pricing()).isNotNull();
        assertThat(response.availability()).isNotNull();
        assertThat(response.personalization()).isNotNull();
    }

    @Test
    void failsWithCatalogUnavailable_whenCatalogThrows() {
        when(catalogClient.fetchProduct(any(), any()))
                .thenThrow(new UpstreamServiceException("CatalogService", "timeout"));

        assertThatThrownBy(() -> service.aggregate("PROD-001", "de-DE", null))
                .isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void partialResponse_whenPricingFails() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(pricingClient.fetchPricing(any(), any(), any()))
                .thenThrow(new UpstreamServiceException("PricingService", "transient"));
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(UpstreamTestFixtures.defaultAvailability());

        ProductResponse response = service.aggregate("PROD-001", "de-DE", null);

        assertThat(response.dataStatus().catalogOk()).isTrue();
        assertThat(response.dataStatus().pricingAvailable()).isFalse();
        assertThat(response.pricing()).isNull();
        assertThat(response.availability()).isNotNull();
        assertThat(response.dataStatus().personalized()).isFalse();
    }

    @Test
    void partialResponse_whenAvailabilityFails() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(UpstreamTestFixtures.defaultPricing());
        when(availabilityClient.fetchAvailability(any(), any()))
                .thenThrow(new UpstreamServiceException("AvailabilityService", "transient"));

        ProductResponse response = service.aggregate("PROD-001", "de-DE", null);

        assertThat(response.dataStatus().availabilityKnown()).isFalse();
        assertThat(response.availability()).isNull();
        assertThat(response.dataStatus().pricingAvailable()).isTrue();
    }

    @Test
    void noPersonalization_whenNoCustomerId() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(pricingClient.fetchPricing(any(), any(), isNull())).thenReturn(UpstreamTestFixtures.defaultPricing());
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(UpstreamTestFixtures.defaultAvailability());

        ProductResponse response = service.aggregate("PROD-001", "de-DE", null);

        assertThat(response.dataStatus().personalized()).isFalse();
        assertThat(response.personalization()).isNull();
        verify(customerClient, never()).fetchCustomer(any());
    }

    @Test
    void partialResponse_whenCustomerServiceFails() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(UpstreamTestFixtures.defaultPricing());
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(UpstreamTestFixtures.defaultAvailability());
        when(customerClient.fetchCustomer(any()))
                .thenThrow(new UpstreamServiceException("CustomerService", "transient"));

        ProductResponse response = service.aggregate("PROD-001", "de-DE", "CUST-1");

        assertThat(response.dataStatus().personalized()).isFalse();
        assertThat(response.personalization()).isNull();
        assertThat(response.dataStatus().catalogOk()).isTrue();
        assertThat(response.dataStatus().pricingAvailable()).isTrue();
    }

    @Test
    void response_containsMarketAndLanguage() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(UpstreamTestFixtures.defaultPricing());
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(UpstreamTestFixtures.defaultAvailability());

        ProductResponse response = service.aggregate("PROD-001", "pl-PL", null);

        assertThat(response.market()).isEqualTo("pl-PL");
        assertThat(response.language()).isEqualTo("pl");
    }

    @Test
    void optionalCircuitBreaker_shortCircuitsAfterThreshold() {
        AggregatorProperties props = new AggregatorProperties();
        props.setCatalogTimeoutMs(500);
        props.setPricingTimeoutMs(500);
        props.setAvailabilityTimeoutMs(500);
        props.setCustomerTimeoutMs(500);
        props.setOptionalFailureThreshold(2);
        props.setOptionalCircuitOpenMs(60_000);

        ProductAggregatorService breakerService = createService(props);

        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(UpstreamTestFixtures.defaultAvailability());
        when(pricingClient.fetchPricing(any(), any(), any()))
                .thenThrow(new UpstreamServiceException("PricingService", "down"));

        breakerService.aggregate("PROD-001", "de-DE", "CUST-1");
        breakerService.aggregate("PROD-001", "de-DE", "CUST-1");
        breakerService.aggregate("PROD-001", "de-DE", "CUST-1");

        verify(pricingClient, times(2)).fetchPricing(any(), any(), any());
    }
}
