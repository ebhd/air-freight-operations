package be.kdg.airfreight.flight.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// flight.actual-times.tolerance from application.properties or FLIGHT_ACTUALTIMES_TOLERANCE
@ConfigurationProperties("flight.actual-times")
record ActualTimeProperties(Duration tolerance) {}
