package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
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

    // stores the enum name and never the ordinal so reordering the enum keeps the data right
    @Enumerated(EnumType.STRING)
    @Column(name = "loading_position", length = 2)
    private LoadingPosition loadingPosition;

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
        entity.loadingPosition = booking.getPosition().orElse(null);
        return entity;
    }

    public UldBooking toDomain() {
        return new UldBooking(
                new UldCode(id.getUldCode()),
                new Weight(weightValue, weightUnit),
                new AirportCode(destinationAirportCode),
                loadingPosition);
    }
}
