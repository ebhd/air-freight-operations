package be.kdg.airfreight.cargo.api.flight;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kdg.airfreight.cargo.application.cargoplan.FinalizeLoadService;
import be.kdg.airfreight.cargo.application.loadcheck.LoadCheckService;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadFinalized;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = CargoPlanSecurityConfiguration.class)
class LoadCheckSecurityTest {
    private static final String URL = "/api/cargo-plans/HJ221/2026-10-19/BRU";
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));

    @Autowired private WebApplicationContext context;
    @Autowired private LoadCheckService loadCheckService;
    @Autowired private FinalizeLoadService finalizeLoadService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        clearInvocations(loadCheckService, finalizeLoadService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(loadCheckService.checkLoad(KEY)).thenReturn(new LoadCheckReport(List.of()));
        when(finalizeLoadService.finalizeLoad(KEY))
                .thenReturn(
                        new LoadFinalized(
                                KEY,
                                Instant.parse("2026-10-19T06:00:00Z"),
                                List.of(),
                                new LoadCheckReport(List.of())));
    }

    @Test
    void loadCheckNeedsToken() throws Exception {
        mvc.perform(get(URL + "/load-check")).andExpect(status().isUnauthorized());
        verifyNoInteractions(loadCheckService);
    }

    // a check only reads so both planners may run it like every get
    @Test
    void bothPlannersMayCheckTheLoad() throws Exception {
        mvc.perform(get(URL + "/load-check").with(planner("load-planner")))
                .andExpect(status().isOk());
        mvc.perform(get(URL + "/load-check").with(planner("flight-planner")))
                .andExpect(status().isOk());
        verify(loadCheckService, times(2)).checkLoad(KEY);
    }

    @Test
    void userWithoutPlannerRoleCannotCheck() throws Exception {
        mvc.perform(get(URL + "/load-check").with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(loadCheckService);
    }

    // no csrf check so a post without a token is refused with 401
    @Test
    void finalizeNeedsToken() throws Exception {
        mvc.perform(post(URL + "/finalization")).andExpect(status().isUnauthorized());
        verifyNoInteractions(finalizeLoadService);
    }

    @Test
    void onlyALoadPlannerMayFinalize() throws Exception {
        mvc.perform(post(URL + "/finalization").with(planner("flight-planner")))
                .andExpect(status().isForbidden());
        mvc.perform(post(URL + "/finalization").with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(finalizeLoadService);

        mvc.perform(post(URL + "/finalization").with(planner("load-planner")))
                .andExpect(status().isOk());
        verify(finalizeLoadService).finalizeLoad(KEY);
    }

    private static RequestPostProcessor planner(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
