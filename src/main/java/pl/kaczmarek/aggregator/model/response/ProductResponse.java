package pl.kaczmarek.aggregator.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductResponse(
        String productId,
        String market,
        String language,

        // From Catalog (required)
        String name,
        String description,
        String category,
        String brand,
        Map<String, String> specifications,
        List<String> imageUrls,

        // From Pricing (optional)
        PricingInfo pricing,

        // From Availability (optional)
        AvailabilityInfo availability,

        // From Customer (optional, only when customerId provided)
        PersonalizationInfo personalization,

        // Meta
        DataStatus dataStatus,
        List<String> degradedReasons
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PricingInfo(
            String currency,
            BigDecimal basePrice,
            BigDecimal discountPercent,
            BigDecimal finalPrice,
            String priceValidUntil
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AvailabilityInfo(
            boolean inStock,
            Integer stockLevel,
            String warehouseLocation,
            String expectedDelivery
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PersonalizationInfo(
            String customerSegment,
            boolean isPremiumCustomer,
            List<String> preferredCategories
    ) {}

    public record DataStatus(
            boolean catalogOk,
            boolean pricingAvailable,
            boolean availabilityKnown,
            boolean personalized
    ) {}
}
