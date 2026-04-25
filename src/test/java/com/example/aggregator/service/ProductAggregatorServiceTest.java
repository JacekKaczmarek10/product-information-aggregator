package com.example.aggregator.service;

import com.example.aggregator.config.AggregatorConfig.AggregatorProperties;
import com.example.aggregator.exception.CatalogUnavailableException;
import com.example.aggregator.model.response.ProductResponse;
import com.example.aggregator.model.upstream.*;
import com.example.aggregator.service.upstream.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAggregatorServiceTest {

    @Mock MockCatalogClient catalogClient;
    @Mock MockPricingClient pricingClient;
    @Mock MockAvailabilityClient availabilityClient;
    @Mock MockCustomerClient customerClient;

    ProductAggregatorService service;

    private static final CatalogData CATALOG = new CatalogData(
            "PROD-001", "Pump", "A pump", "Hydraulics", "AgroTech",
            Map.of("weight_kg", "4.2"), List.of("img1.jpg"), "Hydraulik Pumpe", "Hochleistungspumpe"
    );

    private static final PricingData PRICING = new PricingData(
            "PROD-001", "de-DE", "EUR",
            new BigDecimal("349.99"), BigDecimal.TEN,
            new BigDecimal("314.99"), "2026-04-25"
    );

    private static final AvailabilityData AVAILABILITY = new AvailabilityData(
            "PROD-001", 20, "Frankfurt, DE", "1-2 business days", true
    );

    private static final CustomerData CUSTOMER = new CustomerData(
            "CUST-1", "DEALER", List.of("Hydraulics"), "de", false
    );

    @BeforeEach
    void setUp() {
        AggregatorProperties props = new AggregatorProperties();
        props.setCatalogTimeoutMs(500);
        props.setPricingTimeoutMs(500);
        props.setAvailabilityTimeoutMs(500);
        props.setCustomerTimeoutMs(500);
        service = new ProductAggregatorService(
                catalogClient, pricingClient, availabilityClient, customerClient,
                Executors.newCachedThreadPool(), props, new SimpleMeterRegistry()
        );
    }

    @Test
    void fullResponse_whenAllServicesSucceed() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(PRICING);
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(AVAILABILITY);
        when(customerClient.fetchCustomer(any())).thenReturn(CUSTOMER);

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
        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(pricingClient.fetchPricing(any(), any(), any()))
                .thenThrow(new UpstreamServiceException("PricingService", "transient"));
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(AVAILABILITY);

        ProductResponse response = service.aggregate("PROD-001", "de-DE", null);

        assertThat(response.dataStatus().catalogOk()).isTrue();
        assertThat(response.dataStatus().pricingAvailable()).isFalse();
        assertThat(response.pricing()).isNull();
        assertThat(response.availability()).isNotNull();
        assertThat(response.dataStatus().personalized()).isFalse();
    }

    @Test
    void partialResponse_whenAvailabilityFails() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(PRICING);
        when(availabilityClient.fetchAvailability(any(), any()))
                .thenThrow(new UpstreamServiceException("AvailabilityService", "transient"));

        ProductResponse response = service.aggregate("PROD-001", "de-DE", null);

        assertThat(response.dataStatus().availabilityKnown()).isFalse();
        assertThat(response.availability()).isNull();
        assertThat(response.dataStatus().pricingAvailable()).isTrue();
    }

    @Test
    void noPersonalization_whenNoCustomerId() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(pricingClient.fetchPricing(any(), any(), isNull())).thenReturn(PRICING);
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(AVAILABILITY);

        ProductResponse response = service.aggregate("PROD-001", "de-DE", null);

        assertThat(response.dataStatus().personalized()).isFalse();
        assertThat(response.personalization()).isNull();
        verify(customerClient, never()).fetchCustomer(any());
    }

    @Test
    void partialResponse_whenCustomerServiceFails() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(PRICING);
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(AVAILABILITY);
        when(customerClient.fetchCustomer(any()))
                .thenThrow(new UpstreamServiceException("CustomerService", "transient"));

        ProductResponse response = service.aggregate("PROD-001", "de-DE", "CUST-1");

        assertThat(response.dataStatus().personalized()).isFalse();
        assertThat(response.personalization()).isNull();
        // Core data is still present
        assertThat(response.dataStatus().catalogOk()).isTrue();
        assertThat(response.dataStatus().pricingAvailable()).isTrue();
    }

    @Test
    void response_containsMarketAndLanguage() {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(PRICING);
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(AVAILABILITY);

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

        ProductAggregatorService breakerService = new ProductAggregatorService(
                catalogClient, pricingClient, availabilityClient, customerClient,
                Executors.newCachedThreadPool(), props, new SimpleMeterRegistry()
        );

        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(AVAILABILITY);
        when(pricingClient.fetchPricing(any(), any(), any()))
                .thenThrow(new UpstreamServiceException("PricingService", "down"));

        breakerService.aggregate("PROD-001", "de-DE", "CUST-1");
        breakerService.aggregate("PROD-001", "de-DE", "CUST-1");
        breakerService.aggregate("PROD-001", "de-DE", "CUST-1");

        verify(pricingClient, times(2)).fetchPricing(any(), any(), any());
    }
}
