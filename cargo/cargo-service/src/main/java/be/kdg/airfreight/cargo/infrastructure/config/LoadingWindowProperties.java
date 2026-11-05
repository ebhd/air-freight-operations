package be.kdg.airfreight.cargo.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// cargo.loading-window.* from application.properties or an environment variable
@ConfigurationProperties("cargo.loading-window")
record LoadingWindowProperties(Duration opensBeforeStd, Duration closesBeforeEtd) {}
