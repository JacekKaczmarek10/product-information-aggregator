package pl.kaczmarek.aggregator.service.aggregation;

import pl.kaczmarek.aggregator.model.response.ProductResponse;
import pl.kaczmarek.aggregator.model.response.ProductResponse.AvailabilityInfo;
import pl.kaczmarek.aggregator.model.response.ProductResponse.DataStatus;
import pl.kaczmarek.aggregator.model.response.ProductResponse.PersonalizationInfo;
import pl.kaczmarek.aggregator.model.response.ProductResponse.PricingInfo;
import pl.kaczmarek.aggregator.model.upstream.AvailabilityData;
import pl.kaczmarek.aggregator.model.upstream.CatalogData;
import pl.kaczmarek.aggregator.model.upstream.CustomerData;
import pl.kaczmarek.aggregator.model.upstream.PricingData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ProductResponseMapper {

    public String extractLanguage(String market) {
        return market.contains("-") ? market.split("-")[0] : market;
    }

    public ProductResponse toResponse(
            String productId,
            String market,
            String language,
            CatalogData catalog,
            Optional<PricingData> pricingOpt,
            Optional<AvailabilityData> availabilityOpt,
            Optional<CustomerData> customerOpt) {

        PricingInfo pricingInfo = pricingOpt.map(p -> new PricingInfo(
                p.currency(), p.basePrice(), p.discountPercent(), p.finalPrice(), p.priceValidUntil()
        )).orElse(null);

        AvailabilityInfo availabilityInfo = availabilityOpt.map(a -> new AvailabilityInfo(
                a.inStock(), a.stockLevel(), a.warehouseLocation(), a.expectedDelivery()
        )).orElse(null);

        PersonalizationInfo personalizationInfo = customerOpt.map(c -> new PersonalizationInfo(
                c.segment(), c.isPremium(), c.preferredCategories()
        )).orElse(null);

        DataStatus status = new DataStatus(
                true,
                pricingOpt.isPresent(),
                availabilityOpt.isPresent(),
                customerOpt.isPresent()
        );
        List<String> degradedReasons = degradedReasons(pricingOpt, availabilityOpt, customerOpt);

        return new ProductResponse(
                productId,
                market,
                language,
                catalog.localizedName(),
                catalog.localizedDescription(),
                catalog.category(),
                catalog.brand(),
                catalog.specifications(),
                catalog.imageUrls(),
                pricingInfo,
                availabilityInfo,
                personalizationInfo,
                status,
                degradedReasons.isEmpty() ? null : degradedReasons
        );
    }

    private static List<String> degradedReasons(
            Optional<PricingData> pricingOpt,
            Optional<AvailabilityData> availabilityOpt,
            Optional<CustomerData> customerOpt) {
        List<String> reasons = new ArrayList<>();
        if (pricingOpt.isEmpty()) {
            reasons.add("PRICING_UNAVAILABLE");
        }
        if (availabilityOpt.isEmpty()) {
            reasons.add("AVAILABILITY_UNKNOWN");
        }
        if (customerOpt.isEmpty()) {
            reasons.add("NON_PERSONALIZED");
        }
        return reasons;
    }
}
