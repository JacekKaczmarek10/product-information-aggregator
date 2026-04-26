package pl.kaczmarek.aggregator.controller;

import pl.kaczmarek.aggregator.service.upstream.MockAvailabilityClient;
import pl.kaczmarek.aggregator.service.upstream.MockCatalogClient;
import pl.kaczmarek.aggregator.service.upstream.MockCustomerClient;
import pl.kaczmarek.aggregator.service.upstream.MockPricingClient;
import pl.kaczmarek.aggregator.service.upstream.UpstreamServiceException;
import pl.kaczmarek.aggregator.testsupport.UpstreamTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MockCatalogClient catalogClient;
    @MockitoBean
    MockPricingClient pricingClient;
    @MockitoBean
    MockAvailabilityClient availabilityClient;
    @MockitoBean
    MockCustomerClient customerClient;

    @Test
    void returns200_withFullData() throws Exception {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(UpstreamTestFixtures.defaultPricing());
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(UpstreamTestFixtures.defaultAvailability());

        mockMvc.perform(get("/api/v1/products/PROD-001")
                        .param("market", "de-DE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PROD-001"))
                .andExpect(jsonPath("$.market").value("de-DE"))
                .andExpect(jsonPath("$.dataStatus.catalogOk").value(true))
                .andExpect(jsonPath("$.dataStatus.pricingAvailable").value(true))
                .andExpect(jsonPath("$.dataStatus.availabilityKnown").value(true))
                .andExpect(jsonPath("$.pricing.currency").value("EUR"));
    }

    @Test
    void returns503_whenCatalogFails() throws Exception {
        when(catalogClient.fetchProduct(any(), any()))
                .thenThrow(new UpstreamServiceException("CatalogService", "down"));

        mockMvc.perform(get("/api/v1/products/PROD-001")
                        .param("market", "de-DE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("CATALOG_UNAVAILABLE"));
    }

    @Test
    void returns200WithNullPricing_whenPricingFails() throws Exception {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(pricingClient.fetchPricing(any(), any(), any()))
                .thenThrow(new UpstreamServiceException("PricingService", "transient"));
        when(availabilityClient.fetchAvailability(any(), any()))
                .thenReturn(UpstreamTestFixtures.availabilityFrankfurtLowStock());

        mockMvc.perform(get("/api/v1/products/PROD-001")
                        .param("market", "de-DE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataStatus.pricingAvailable").value(false))
                .andExpect(jsonPath("$.pricing").doesNotExist());
    }

    @Test
    void returns400_whenMarketInvalidFormat() throws Exception {
        mockMvc.perform(get("/api/v1/products/PROD-001")
                        .param("market", "invalid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400_whenMarketMissing() throws Exception {
        mockMvc.perform(get("/api/v1/products/PROD-001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void includesPersonalization_whenCustomerIdProvided() throws Exception {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(UpstreamTestFixtures.defaultCatalog());
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(UpstreamTestFixtures.pricingNlNlProd001());
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(UpstreamTestFixtures.availabilityAmsterdamProd001());
        when(customerClient.fetchCustomer(eq("CUST-42"))).thenReturn(UpstreamTestFixtures.customerCust42());

        mockMvc.perform(get("/api/v1/products/PROD-001")
                        .param("market", "nl-NL")
                        .param("customerId", "CUST-42")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataStatus.personalized").value(true))
                .andExpect(jsonPath("$.personalization.customerSegment").value("DEALER"));
    }
}
