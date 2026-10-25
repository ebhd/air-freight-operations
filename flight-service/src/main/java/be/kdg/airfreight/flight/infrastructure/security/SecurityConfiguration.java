package be.kdg.airfreight.flight.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(HttpMethod.GET, "/api/**")
                                        .hasAnyRole("flight-planner", "load-planner")
                                        .requestMatchers("/api/**")
                                        .hasRole("flight-planner")
                                        .anyRequest()
                                        .permitAll())
                .oauth2ResourceServer(
                        server ->
                                server.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        keycloakAuthentication())))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .build();
    }

    private static JwtAuthenticationConverter keycloakAuthentication() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }
}
