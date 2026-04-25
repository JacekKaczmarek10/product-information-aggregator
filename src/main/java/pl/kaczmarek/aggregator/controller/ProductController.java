package pl.kaczmarek.aggregator.controller;

import pl.kaczmarek.aggregator.model.response.ErrorResponse;
import pl.kaczmarek.aggregator.model.response.ProductResponse;
import pl.kaczmarek.aggregator.service.ProductAggregatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@Validated
@Tag(name = "Product Aggregator", description = "Aggregates product information from multiple upstream services")
public class ProductController {

    private final ProductAggregatorService aggregatorService;

    public ProductController(ProductAggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("/{productId}")
    @Operation(
            summary = "Get aggregated product information",
            description = """
                    Returns aggregated product information combining Catalog, Pricing, Availability,
                    and optionally Customer data. Catalog data is required — if unavailable, returns 503.
                    Pricing and Availability failures yield a partial response with status flags.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found (may be partial if optional services degraded)"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Catalog service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable
            @NotBlank
            @Parameter(description = "Product identifier", example = "PROD-001")
            String productId,

            @RequestParam
            @NotBlank
            @Pattern(regexp = "[a-z]{2}-[A-Z]{2}", message = "market must be in the format 'll-CC' (e.g. nl-NL)")
            @Parameter(description = "Market/locale code", example = "nl-NL")
            String market,

            @RequestParam(required = false)
            @Parameter(description = "Optional customer ID for personalized pricing and recommendations", example = "CUST-9876")
            String customerId) {

        ProductResponse response = aggregatorService.aggregate(productId, market, customerId);
        return ResponseEntity.ok(response);
    }
}
