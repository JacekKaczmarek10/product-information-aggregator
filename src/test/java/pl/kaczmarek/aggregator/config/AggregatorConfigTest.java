package pl.kaczmarek.aggregator.config;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutorService;
import static org.assertj.core.api.Assertions.assertThat;

class AggregatorConfigTest {

    private final AggregatorConfig config = new AggregatorConfig();

    @Test
    void shouldCreateUpstreamExecutor() {
        ExecutorService executor = config.upstreamExecutor();
        assertThat(executor).isNotNull();
        executor.shutdown();
    }

    @Test
    void shouldCreateAggregatorPropertiesWithDefaultValues() {
        AggregatorConfig.AggregatorProperties props = config.aggregatorProperties();
        
        assertThat(props).isNotNull();
        assertThat(props.getTimeoutMs()).isEqualTo(300);
        assertThat(props.getCatalogTimeoutMs()).isEqualTo(150);
        assertThat(props.getPricingTimeoutMs()).isEqualTo(180);
        assertThat(props.getAvailabilityTimeoutMs()).isEqualTo(200);
        assertThat(props.getCustomerTimeoutMs()).isEqualTo(160);
        assertThat(props.getOptionalFailureThreshold()).isEqualTo(3);
        assertThat(props.getOptionalCircuitOpenMs()).isEqualTo(30000L);
        assertThat(props.getMarkets()).isEmpty();
    }

    @Test
    void shouldSetAndGetProperties() {
        AggregatorConfig.AggregatorProperties props = new AggregatorConfig.AggregatorProperties();
        props.setTimeoutMs(500);
        props.setPricingTimeoutMs(250);
        
        assertThat(props.getTimeoutMs()).isEqualTo(500);
        assertThat(props.getPricingTimeoutMs()).isEqualTo(250);
    }

    @Test
    void shouldCreateMarketSettingsWithDefaultValues() {
        AggregatorConfig.MarketSettings settings = new AggregatorConfig.MarketSettings();
        
        assertThat(settings.getCurrency()).isEqualTo("EUR");
        assertThat(settings.getWarehouse()).isEqualTo("Central EU Warehouse");
        assertThat(settings.getDelivery()).isEqualTo("3-5 business days");
        assertThat(settings.getLanguage()).isEqualTo("en");
        assertThat(settings.getFx()).isEqualTo(1.0);
    }

    @Test
    void shouldSetAndGetMarketSettings() {
        AggregatorConfig.MarketSettings settings = new AggregatorConfig.MarketSettings();
        settings.setCurrency("PLN");
        settings.setFx(4.3);
        
        assertThat(settings.getCurrency()).isEqualTo("PLN");
        assertThat(settings.getFx()).isEqualTo(4.3);
    }
}
