package be.kdg.airfreight.flight.infrastructure;

import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

// flight.retention.period from application.properties or FLIGHT_RETENTION_PERIOD
@ConfigurationProperties("flight.retention")
record RetentionProperties(Period period) {}
