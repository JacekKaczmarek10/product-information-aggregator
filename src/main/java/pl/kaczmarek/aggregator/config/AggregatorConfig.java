package pl.kaczmarek.aggregator.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableCaching
public class AggregatorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService upstreamExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    @ConfigurationProperties(prefix = "aggregator")
    public AggregatorProperties aggregatorProperties() {
        return new AggregatorProperties();
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("catalog");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1_000)
                .recordStats());
        return manager;
    }

    @Getter
    @Setter
    public static class AggregatorProperties {
        private int timeoutMs = 300;
        private int catalogTimeoutMs = 150;
        private int pricingTimeoutMs = 180;
        private int availabilityTimeoutMs = 200;
        private int customerTimeoutMs = 160;
        private int optionalFailureThreshold = 3;
        private long optionalCircuitOpenMs = 30000;
        private Map<String, MarketSettings> markets = new HashMap<>();
    }

    @Getter
    @Setter
    public static class MarketSettings {
        private String currency = "EUR";
        private String warehouse = "Central EU Warehouse";
        private String delivery = "3-5 business days";
        private String language = "en";
        private double fx = 1.0;
    }
}
