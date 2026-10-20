package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// db allows one booking per uld code per flight
@Embeddable
public class JpaUldBookingId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "flight_number", nullable = false, length = 32)
    private String flightNumber;

    @Column(name = "scheduled_departure_date", nullable = false)
    private LocalDate scheduledDepartureDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "departure_airport_code", nullable = false, length = 3)
    private String departureAirportCode;

    @Column(name = "uld_code", nullable = false, length = 32)
    private String uldCode;

    protected JpaUldBookingId() {}

    public JpaUldBookingId(
            String flightNumber,
            LocalDate scheduledDepartureDate,
            String departureAirportCode,
            String uldCode) {
        this.flightNumber = flightNumber;
        this.scheduledDepartureDate = scheduledDepartureDate;
        this.departureAirportCode = departureAirportCode;
        this.uldCode = uldCode;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public LocalDate getScheduledDepartureDate() {
        return scheduledDepartureDate;
    }

    public String getDepartureAirportCode() {
        return departureAirportCode;
    }

    public String getUldCode() {
        return uldCode;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof JpaUldBookingId id
                        && Objects.equals(flightNumber, id.flightNumber)
                        && Objects.equals(scheduledDepartureDate, id.scheduledDepartureDate)
                        && Objects.equals(departureAirportCode, id.departureAirportCode)
                        && Objects.equals(uldCode, id.uldCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flightNumber, scheduledDepartureDate, departureAirportCode, uldCode);
    }
}
