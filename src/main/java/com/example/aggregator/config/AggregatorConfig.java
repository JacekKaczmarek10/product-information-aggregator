package com.example.aggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
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

    public static class AggregatorProperties {
        private int timeoutMs = 300;
        private int catalogTimeoutMs = 150;
        private int pricingTimeoutMs = 180;
        private int availabilityTimeoutMs = 200;
        private int customerTimeoutMs = 160;
        private int optionalFailureThreshold = 3;
        private long optionalCircuitOpenMs = 30000;
        private Map<String, MarketSettings> markets = new HashMap<>();

        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

        public int getCatalogTimeoutMs() { return catalogTimeoutMs; }
        public void setCatalogTimeoutMs(int catalogTimeoutMs) { this.catalogTimeoutMs = catalogTimeoutMs; }

        public int getPricingTimeoutMs() { return pricingTimeoutMs; }
        public void setPricingTimeoutMs(int pricingTimeoutMs) { this.pricingTimeoutMs = pricingTimeoutMs; }

        public int getAvailabilityTimeoutMs() { return availabilityTimeoutMs; }
        public void setAvailabilityTimeoutMs(int availabilityTimeoutMs) { this.availabilityTimeoutMs = availabilityTimeoutMs; }

        public int getCustomerTimeoutMs() { return customerTimeoutMs; }
        public void setCustomerTimeoutMs(int customerTimeoutMs) { this.customerTimeoutMs = customerTimeoutMs; }

        public int getOptionalFailureThreshold() { return optionalFailureThreshold; }
        public void setOptionalFailureThreshold(int optionalFailureThreshold) { this.optionalFailureThreshold = optionalFailureThreshold; }

        public long getOptionalCircuitOpenMs() { return optionalCircuitOpenMs; }
        public void setOptionalCircuitOpenMs(long optionalCircuitOpenMs) { this.optionalCircuitOpenMs = optionalCircuitOpenMs; }

        public Map<String, MarketSettings> getMarkets() { return markets; }
        public void setMarkets(Map<String, MarketSettings> markets) { this.markets = markets; }
    }

    public static class MarketSettings {
        private String currency = "EUR";
        private String warehouse = "Central EU Warehouse";
        private String delivery = "3-5 business days";
        private String language = "en";
        private double fx = 1.0;

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public String getWarehouse() { return warehouse; }
        public void setWarehouse(String warehouse) { this.warehouse = warehouse; }

        public String getDelivery() { return delivery; }
        public void setDelivery(String delivery) { this.delivery = delivery; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public double getFx() { return fx; }
        public void setFx(double fx) { this.fx = fx; }
    }
}
