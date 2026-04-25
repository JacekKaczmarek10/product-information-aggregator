package com.example.aggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    }
}
