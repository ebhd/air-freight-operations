package be.kdg.airfreight.cargo.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers("/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        // page files hold no data so they load without a token
                                        .requestMatchers(
                                                HttpMethod.GET, "/", "/*.html", "/css/**", "/js/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }
}
