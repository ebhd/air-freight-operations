package be.kdg.airfreight.cargo.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoadingPositionControllerTest {
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new LoadingPositionController()).build();

    @Test
    void listsThePositionsInOrder() throws Exception {
        mvc.perform(get("/api/loading-positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(34)))
                .andExpect(jsonPath("$[0].code").value("A1"))
                .andExpect(jsonPath("$[0].row").value("A"))
                .andExpect(jsonPath("$[0].side").value("NONE"))
                .andExpect(jsonPath("$[3].code").value("CL"))
                .andExpect(jsonPath("$[3].side").value("LEFT"))
                .andExpect(jsonPath("$[20].code").value("LR"))
                .andExpect(jsonPath("$[20].row").value("L"))
                .andExpect(jsonPath("$[20].side").value("RIGHT"))
                .andExpect(jsonPath("$[25].code").value("RL"))
                .andExpect(jsonPath("$[25].row").value("R"))
                .andExpect(jsonPath("$[25].side").value("LEFT"))
                .andExpect(jsonPath("$[33].code").value("X"))
                .andExpect(jsonPath("$[33].side").value("NONE"));
    }
}
