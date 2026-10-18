package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.UldBooking;
import be.kdg.airfreight.cargo.domain.UldCode;
import be.kdg.airfreight.cargo.domain.Weight;
import be.kdg.airfreight.cargo.domain.WeightUnit;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "uld_bookings")
public class JpaUldBooking {
    @EmbeddedId private JpaUldBookingId id;

    @Column(name = "weight_value", nullable = false)
    private BigDecimal weightValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_unit", nullable = false, length = 2)
    private WeightUnit weightUnit;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "destination_airport_code", nullable = false, length = 3)
    private String destinationAirportCode;

    protected JpaUldBooking() {}

    public static JpaUldBooking fromDomain(FlightKey flightKey, UldBooking booking) {
        JpaUldBooking entity = new JpaUldBooking();
        entity.id =
                new JpaUldBookingId(
                        flightKey.flightNumber(),
                        flightKey.scheduledDepartureDate(),
                        flightKey.departureAirportCode().value(),
                        booking.getCode().value());
        entity.weightValue = booking.getWeight().value();
        entity.weightUnit = booking.getWeight().unit();
        entity.destinationAirportCode = booking.getDestination().value();
        return entity;
    }

    public UldBooking toDomain() {
        return new UldBooking(
                new UldCode(id.getUldCode()),
                new Weight(weightValue, weightUnit),
                new AirportCode(destinationAirportCode));
    }
}
