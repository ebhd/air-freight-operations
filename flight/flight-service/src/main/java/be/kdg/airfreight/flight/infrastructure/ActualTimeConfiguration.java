package be.kdg.airfreight.flight.infrastructure;

import be.kdg.airfreight.flight.domain.flight.actualtime.ActualTimePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ActualTimeProperties.class)
@Slf4j
class ActualTimeConfiguration {
    // a missing or negative tolerance stops the startup with the message of the policy
    @Bean
    ActualTimePolicy actualTimePolicy(ActualTimeProperties properties) {
        ActualTimePolicy policy = new ActualTimePolicy(properties.tolerance());
        log.info("Actual times may be at most {} later than the estimate", policy.tolerance());
        return policy;
    }
}
