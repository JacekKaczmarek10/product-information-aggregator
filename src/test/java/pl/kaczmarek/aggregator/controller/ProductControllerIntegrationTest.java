package pl.kaczmarek.aggregator.controller;

import pl.kaczmarek.aggregator.model.upstream.*;
import  pl.kaczmarek.aggregator.service.upstream.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.kaczmarek.aggregator.model.upstream.AvailabilityData;
import pl.kaczmarek.aggregator.model.upstream.CatalogData;
import pl.kaczmarek.aggregator.model.upstream.CustomerData;
import pl.kaczmarek.aggregator.model.upstream.PricingData;
import pl.kaczmarek.aggregator.service.upstream.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private static final CatalogData CATALOG = new CatalogData(
            "PROD-001", "Pump", "A pump", "Hydraulics", "AgroTech",
            Map.of("weight_kg", "4.2"), List.of("img1.jpg"), "Hydraulik Pumpe", "Hochleistungspumpe"
    );

    @Test
    void returns200_withFullData() throws Exception {
        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(
                new PricingData("PROD-001", "de-DE", "EUR",
                        new BigDecimal("349.99"), BigDecimal.TEN, new BigDecimal("314.99"), "2026-04-25"));
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(
                new AvailabilityData("PROD-001", 20, "Frankfurt, DE", "1-2 business days", true));

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
        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(pricingClient.fetchPricing(any(), any(), any()))
                .thenThrow(new UpstreamServiceException("PricingService", "transient"));
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(
                new AvailabilityData("PROD-001", 5, "Frankfurt, DE", "2 days", true));

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
        when(catalogClient.fetchProduct(any(), any())).thenReturn(CATALOG);
        when(pricingClient.fetchPricing(any(), any(), any())).thenReturn(
                new PricingData("PROD-001", "nl-NL", "EUR",
                        new BigDecimal("349.99"), new BigDecimal("5"), new BigDecimal("332.49"), "2026-04-25"));
        when(availabilityClient.fetchAvailability(any(), any())).thenReturn(
                new AvailabilityData("PROD-001", 10, "Amsterdam, NL", "1-2 business days", true));
        when(customerClient.fetchCustomer(eq("CUST-42"))).thenReturn(
                new CustomerData("CUST-42", "DEALER", List.of("Hydraulics"), "nl", false));

        mockMvc.perform(get("/api/v1/products/PROD-001")
                        .param("market", "nl-NL")
                        .param("customerId", "CUST-42")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataStatus.personalized").value(true))
                .andExpect(jsonPath("$.personalization.customerSegment").value("DEALER"));
    }
}
