package pl.kaczmarek.aggregator.testsupport;

import pl.kaczmarek.aggregator.model.upstream.AvailabilityData;
import pl.kaczmarek.aggregator.model.upstream.CatalogData;
import pl.kaczmarek.aggregator.model.upstream.CustomerData;
import pl.kaczmarek.aggregator.model.upstream.PricingData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Shared upstream DTOs for unit and integration tests.
 */
public final class UpstreamTestFixtures {

    private UpstreamTestFixtures() {
    }

    public static CatalogData defaultCatalog() {
        return new CatalogData(
                "PROD-001",
                "Pump",
                "A pump",
                "Hydraulics",
                "AgroTech",
                Map.of("weight_kg", "4.2"),
                List.of("img1.jpg"),
                "Hydraulik Pumpe",
                "Hochleistungspumpe"
        );
    }

    public static PricingData defaultPricing() {
        return new PricingData(
                "PROD-001",
                "de-DE",
                "EUR",
                new BigDecimal("349.99"),
                BigDecimal.TEN,
                new BigDecimal("314.99"),
                "2026-04-25"
        );
    }

    public static AvailabilityData defaultAvailability() {
        return new AvailabilityData(
                "PROD-001",
                20,
                "Frankfurt, DE",
                "1-2 business days",
                true
        );
    }

    public static CustomerData defaultCustomer() {
        return new CustomerData(
                "CUST-1",
                "DEALER",
                List.of("Hydraulics"),
                "de",
                false
        );
    }

    /** Used when tests need availability data distinct from {@link #defaultAvailability()}. */
    public static AvailabilityData availabilityFrankfurtLowStock() {
        return new AvailabilityData(
                "PROD-001",
                5,
                "Frankfurt, DE",
                "2 days",
                true
        );
    }

    public static PricingData pricingNlNlProd001() {
        return new PricingData(
                "PROD-001",
                "nl-NL",
                "EUR",
                new BigDecimal("349.99"),
                new BigDecimal("5"),
                new BigDecimal("332.49"),
                "2026-04-25"
        );
    }

    public static AvailabilityData availabilityAmsterdamProd001() {
        return new AvailabilityData(
                "PROD-001",
                10,
                "Amsterdam, NL",
                "1-2 business days",
                true
        );
    }

    public static CustomerData customerCust42() {
        return new CustomerData(
                "CUST-42",
                "DEALER",
                List.of("Hydraulics"),
                "nl",
                false
        );
    }
}
