package be.kdg.airfreight.cargo.infrastructure.persistence;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightCopyRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// jdbc because only newer must be one atomic statement which jpa cannot express
@Repository
class DbFlightCopyRepository implements FlightCopyRepository {
    private static final String COLUMNS =
            "flight_number, scheduled_departure_date, departure_airport_code,"
                    + " arrival_airport_code, status, scheduled_departure, scheduled_arrival,"
                    + " estimated_departure, estimated_arrival";

    private final JdbcClient jdbc;

    DbFlightCopyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlightInformation> findCopy(FlightKey flightKey) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM flights WHERE flight_number = :flightNumber"
                                + " AND scheduled_departure_date = :date"
                                + " AND departure_airport_code = :departure")
                .param("flightNumber", flightKey.flightNumber())
                .param("date", flightKey.scheduledDepartureDate())
                .param("departure", flightKey.departureAirportCode().value())
                .query(DbFlightCopyRepository::toFlight)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlightInformation> findCopiesOn(LocalDate scheduledDepartureDate) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM flights WHERE scheduled_departure_date = :date"
                                + " ORDER BY scheduled_departure, flight_number")
                .param("date", scheduledDepartureDate)
                .query(DbFlightCopyRepository::toFlight)
                .list();
    }

    // own transaction so a fetched flight stays in the copy when its booking is refused
    // the status of an existing row is left alone so a late event cannot undo a departure
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveIfNewer(FlightInformation flight, Instant changedAt) {
        int changed =
                jdbc.sql(
                                "INSERT INTO flights ("
                                        + COLUMNS
                                        + ", last_change_at) VALUES (:flightNumber, :date,"
                                        + " :departure, :arrival, :status, :std, :sta, :etd, :eta,"
                                        + " :changedAt)"
                                        + " ON CONFLICT (flight_number, scheduled_departure_date,"
                                        + " departure_airport_code) DO UPDATE SET"
                                        + " arrival_airport_code = EXCLUDED.arrival_airport_code,"
                                        + " scheduled_departure = EXCLUDED.scheduled_departure,"
                                        + " scheduled_arrival = EXCLUDED.scheduled_arrival,"
                                        + " estimated_departure = EXCLUDED.estimated_departure,"
                                        + " estimated_arrival = EXCLUDED.estimated_arrival,"
                                        + " last_change_at = EXCLUDED.last_change_at"
                                        + " WHERE flights.last_change_at < EXCLUDED.last_change_at")
                        .param("flightNumber", flight.flightKey().flightNumber())
                        .param("date", flight.flightKey().scheduledDepartureDate())
                        .param("departure", flight.flightKey().departureAirportCode().value())
                        .param("arrival", flight.arrivalAirportCode().value())
                        .param("status", flight.status().name())
                        .param("std", Timestamp.from(flight.scheduledDeparture()))
                        .param("sta", Timestamp.from(flight.scheduledArrival()))
                        .param("etd", Timestamp.from(flight.estimatedDeparture()))
                        .param("eta", Timestamp.from(flight.estimatedArrival()))
                        .param("changedAt", Timestamp.from(changedAt))
                        .update();
        return changed == 1;
    }

    // an update not an upsert because an estimate alone is not enough for a new row
    @Override
    @Transactional
    public boolean saveEstimatesIfNewer(
            FlightKey flightKey,
            Instant estimatedDeparture,
            Instant estimatedArrival,
            Instant changedAt) {
        int changed =
                jdbc.sql(
                                "UPDATE flights SET estimated_departure = :etd,"
                                        + " estimated_arrival = :eta, last_change_at = :changedAt"
                                        + " WHERE flight_number = :flightNumber"
                                        + " AND scheduled_departure_date = :date"
                                        + " AND departure_airport_code = :departure"
                                        + " AND last_change_at < :changedAt")
                        .param("etd", Timestamp.from(estimatedDeparture))
                        .param("eta", Timestamp.from(estimatedArrival))
                        .param("changedAt", Timestamp.from(changedAt))
                        .param("flightNumber", flightKey.flightNumber())
                        .param("date", flightKey.scheduledDepartureDate())
                        .param("departure", flightKey.departureAirportCode().value())
                        .update();
        return changed == 1;
    }

    // status in (earlier statuses) makes it one atomic statement that is idempotent
    @Override
    @Transactional
    public boolean advanceStatus(FlightKey flightKey, FlightStatus status) {
        List<String> earlier = status.predecessors().stream().map(FlightStatus::name).toList();
        int changed =
                jdbc.sql(
                                "UPDATE flights SET status = :status"
                                        + " WHERE flight_number = :flightNumber"
                                        + " AND scheduled_departure_date = :date"
                                        + " AND departure_airport_code = :departure"
                                        + " AND status IN (:earlier)")
                        .param("status", status.name())
                        .param("flightNumber", flightKey.flightNumber())
                        .param("date", flightKey.scheduledDepartureDate())
                        .param("departure", flightKey.departureAirportCode().value())
                        .param("earlier", earlier)
                        .update();
        return changed == 1;
    }

    // joins the transaction of the caller so the copy goes together with the plan
    @Override
    @Transactional
    public void delete(FlightKey flightKey) {
        jdbc.sql(
                        "DELETE FROM flights WHERE flight_number = :flightNumber"
                                + " AND scheduled_departure_date = :date"
                                + " AND departure_airport_code = :departure")
                .param("flightNumber", flightKey.flightNumber())
                .param("date", flightKey.scheduledDepartureDate())
                .param("departure", flightKey.departureAirportCode().value())
                .update();
    }

    private static FlightInformation toFlight(ResultSet row, int rowNumber) throws SQLException {
        FlightKey key =
                new FlightKey(
                        row.getString("flight_number"),
                        row.getObject("scheduled_departure_date", LocalDate.class),
                        new AirportCode(row.getString("departure_airport_code")));
        return new FlightInformation(
                key,
                new AirportCode(row.getString("arrival_airport_code")),
                FlightStatus.valueOf(row.getString("status")),
                row.getTimestamp("scheduled_departure").toInstant(),
                row.getTimestamp("scheduled_arrival").toInstant(),
                row.getTimestamp("estimated_departure").toInstant(),
                row.getTimestamp("estimated_arrival").toInstant());
    }
}
