package be.kdg.airfreight.cargo.infrastructure;

import be.kdg.airfreight.cargo.domain.LoadingWindowPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LoadingWindowProperties.class)
@Slf4j
class LoadingWindowConfiguration {
    // invalid offsets stop the startup with the message of the policy
    @Bean
    LoadingWindowPolicy loadingWindowPolicy(LoadingWindowProperties properties) {
        LoadingWindowPolicy policy =
                new LoadingWindowPolicy(properties.opensBeforeStd(), properties.closesBeforeEtd());
        log.info(
                "Loading window opens {} before STD and closes {} before ETD",
                policy.opensBeforeStd(),
                policy.closesBeforeEtd());
        return policy;
    }
}
