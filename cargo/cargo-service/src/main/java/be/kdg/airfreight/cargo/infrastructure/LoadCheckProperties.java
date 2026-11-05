package be.kdg.airfreight.cargo.infrastructure;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

// cargo.load-check.* from application.properties or an environment variable
@ConfigurationProperties("cargo.load-check")
record LoadCheckProperties(BigDecimal lateralBalanceTolerance) {}
