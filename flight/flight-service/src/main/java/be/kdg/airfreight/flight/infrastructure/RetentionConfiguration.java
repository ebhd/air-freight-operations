package be.kdg.airfreight.flight.infrastructure;

import be.kdg.airfreight.flight.domain.flight.RetentionPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RetentionProperties.class)
@Slf4j
class RetentionConfiguration {
    // a missing, zero or negative period stops the startup with the message of the policy
    @Bean
    RetentionPolicy retentionPolicy(RetentionProperties properties) {
        RetentionPolicy policy = new RetentionPolicy(properties.period());
        log.info("Flights are removed {} after their scheduled departure date", policy.period());
        return policy;
    }
}
