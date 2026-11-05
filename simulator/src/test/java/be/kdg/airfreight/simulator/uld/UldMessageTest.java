package be.kdg.airfreight.simulator.uld;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UldMessageTest {
    @ParameterizedTest
    @CsvSource({"2026-10-26, 26", "2026-10-01, 1", "2026-10-31, 31", "2028-02-29, 29"})
    void convertsFullDateToDayOfMonth(LocalDate scheduledDepartureDate, int expectedDay) {
        UldRequest request =
                new UldRequest(
                        null,
                        "HJ221",
                        scheduledDepartureDate,
                        "BRU",
                        "AKE12345KL",
                        new UldWeight(new BigDecimal("1250.5"), "KG"),
                        "AMS");

        assertThat(UldMessage.from("uld-1", request).scheduledDepartureDay())
                .isEqualTo(expectedDay);
    }

    @Test
    void copiesOtherFieldsUnchanged() {
        // cargo validates these so the simulator must not fix or refuse them
        UldWeight weight = new UldWeight(new BigDecimal("-5"), "ton");
        UldRequest request =
                new UldRequest(
                        null,
                        "hj221",
                        LocalDate.of(2026, 10, 26),
                        "bru",
                        "ake12345kl",
                        weight,
                        "ams");

        UldMessage message = UldMessage.from("uld-1", request);

        assertThat(message)
                .isEqualTo(
                        new UldMessage(
                                "uld-1", 1, "hj221", 26, "bru", "ake12345kl", weight, "ams"));
    }
}
