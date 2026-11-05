package be.kdg.airfreight.cargo.infrastructure.config;

import be.kdg.airfreight.cargo.domain.loadcheck.LateralBalanceCheck;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadChecks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LoadCheckProperties.class)
@Slf4j
class LoadCheckConfiguration {
    // a missing or negative tolerance stops the startup with the message of the check
    @Bean
    LoadChecks loadChecks(LoadCheckProperties properties) {
        LateralBalanceCheck lateralBalance =
                new LateralBalanceCheck(properties.lateralBalanceTolerance());
        log.info(
                "Lateral balance: heavier side at most lighter side times 1 + {}",
                lateralBalance.tolerance());
        return new LoadChecks(lateralBalance);
    }
}
