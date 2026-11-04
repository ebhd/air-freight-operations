package be.kdg.airfreight.cargo.api.flight;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loading.LoadingNotAllowedException;
import be.kdg.airfreight.cargo.domain.loading.LoadingRefusal;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindow;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

class CargoApiExceptionHandlerTest {
    // the 404 and 503 answers are covered by the controller tests
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new RefusingController())
                    .setControllerAdvice(new CargoApiExceptionHandler())
                    .build();

    @Test
    void loadingNotAllowedIsConflictWithRefusal() throws Exception {
        mvc.perform(put("/refused"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Loading on HJ221/2026-10-22/BRU is not allowed: the"
                                                + " loading window closed at 2026-10-22T07:40:00Z"))
                .andExpect(jsonPath("$.refusal").value("WINDOW_CLOSED"))
                .andExpect(jsonPath("$.opensAt").value("2026-10-21T20:00:00Z"))
                .andExpect(jsonPath("$.closesAt").value("2026-10-22T07:40:00Z"));
    }

    // elsewhere it can mean a bug so only CargoPlanController turns it into a 409
    @Test
    void dataIntegrityViolationIsNotMappedForEveryEndpoint() {
        assertThatThrownBy(() -> mvc.perform(put("/integrity")))
                .hasRootCauseInstanceOf(DataIntegrityViolationException.class);
    }

    // stands in for a later load changing endpoint like the positions of issue 20
    @RestController
    static class RefusingController {
        @PutMapping("/refused")
        void refuse() {
            throw new LoadingNotAllowedException(
                    new FlightKey("HJ221", LocalDate.of(2026, 10, 22), new AirportCode("BRU")),
                    LoadingRefusal.WINDOW_CLOSED,
                    new LoadingWindow(
                            Instant.parse("2026-10-21T20:00:00Z"),
                            Instant.parse("2026-10-22T07:40:00Z")));
        }

        @PutMapping("/integrity")
        void violate() {
            throw new DataIntegrityViolationException("duplicate key");
        }
    }
}
