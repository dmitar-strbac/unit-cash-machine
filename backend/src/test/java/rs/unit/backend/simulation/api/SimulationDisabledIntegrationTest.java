package rs.unit.backend.simulation.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SimulationDisabledIntegrationTest {

    @Autowired private WebApplicationContext applicationContext;

    @Test
    void simulationControllerIsNotAvailableWithoutSimulationProfile() throws Exception {
        assertTrue(applicationContext.getBeansOfType(SimulationController.class).isEmpty());

        MockMvcBuilders.webAppContextSetup(applicationContext)
                .build()
                .perform(
                        put("/api/simulation/hardware")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isNotFound());
    }
}
