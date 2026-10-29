package rs.unit.backend.simulation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.simulator.SimulatedHardware;
import rs.unit.backend.support.MachineTestState;

@SpringBootTest(
        properties = {
            "unit.collection.monitor.enabled=false",
            "unit.deposit.monitor.enabled=false",
            "unit.maintenance.monitor.enabled=false"
        })
@ActiveProfiles("simulation")
@Transactional
class SimulationIntegrationTest {

    @Autowired private WebApplicationContext applicationContext;

    @Autowired private SimulatedHardware simulatedHardware;

    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private HardwareSnapshot originalState;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.webAppContextSetup(applicationContext)
                        .apply(springSecurity())
                        .build();
        MachineTestState.reset(jdbcTemplate);
        originalState = simulatedHardware.snapshot();
    }

    @AfterEach
    void restoreState() {
        simulatedHardware.replaceState(originalState);
    }

    @Test
    void offlinePrinterDisablesPrintingButNotDeposit() throws Exception {
        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .with(user("test-maintenance").roles("MAINTENANCE"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(stateJson("CLOSED", "LOCKED", "OFFLINE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printer").value("OFFLINE"));

        mockMvc.perform(get("/api/machine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printingAvailable").value(false))
                .andExpect(jsonPath("$.depositHardwareReady").value(true))
                .andExpect(jsonPath("$.indicator").value("AMBER"))
                .andExpect(jsonPath("$.alertCount").value(1))
                .andExpect(jsonPath("$.alerts[0].code").value("PRINTER_OFFLINE"))
                .andExpect(jsonPath("$.alerts[0].source").value("PRINTER"))
                .andExpect(jsonPath("$.alerts[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.alerts[0].message").value("Not operational"))
                .andExpect(jsonPath("$.alerts[0].blocksDeposit").value(false));

        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .with(user("test-maintenance").roles("MAINTENANCE"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(stateJson("CLOSED", "LOCKED", "READY")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/machine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printingAvailable").value(true))
                .andExpect(jsonPath("$.indicator").value("GREEN"))
                .andExpect(jsonPath("$.alertCount").value(0))
                .andExpect(jsonPath("$.alerts").isEmpty());
    }

    @Test
    void openingDoorBlocksDepositAndRestoringStateAllowsItAgain() throws Exception {
        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .with(user("test-maintenance").roles("MAINTENANCE"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(stateJson("OPEN", "UNLOCKED", "READY")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/machine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secure").value(false))
                .andExpect(jsonPath("$.depositHardwareReady").value(false));

        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .with(user("test-maintenance").roles("MAINTENANCE"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(stateJson("CLOSED", "LOCKED", "READY")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/machine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secure").value(true))
                .andExpect(jsonPath("$.depositHardwareReady").value(true));
    }

    @Test
    void repeatingSameRequestPreservesSameState() throws Exception {
        String body = stateJson("CLOSED", "LOCKED", "OFFLINE");

        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .with(user("test-maintenance").roles("MAINTENANCE"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        HardwareSnapshot afterFirstRequest = simulatedHardware.snapshot();

        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .with(user("test-maintenance").roles("MAINTENANCE"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        assertEquals(afterFirstRequest, simulatedHardware.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"acceptor", "cashbox", "door", "lock", "extensionBoard", "printer"})
    void missingRequiredFieldIsRejectedWithoutChangingState(String field) throws Exception {
        String validBody = stateJson("CLOSED", "LOCKED", "READY");

        String invalidBody =
                validBody.replaceFirst("\"" + field + "\"\\s*:\\s*\"[^\"]+\"\\s*,?", "");

        invalidBody = invalidBody.replaceAll(",\\s*}", "}");

        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .with(user("test-maintenance").roles("MAINTENANCE"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(originalState, simulatedHardware.snapshot());
    }

    @Test
    void unknownEnumValueIsRejectedWithoutChangingState() throws Exception {
        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .with(user("test-maintenance").roles("MAINTENANCE"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(stateJson("CLOSED", "LOCKED", "BROKEN_VALUE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(originalState, simulatedHardware.snapshot());
    }

    @Test
    void malformedJsonIsRejectedWithoutChangingState() throws Exception {
        mockMvc.perform(
                        put("/api/simulation/hardware")
                                .with(user("test-maintenance").roles("MAINTENANCE"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(originalState, simulatedHardware.snapshot());
    }

    private String stateJson(String door, String lock, String printer) {
        return """
                {
                  "acceptor": "READY",
                  "cashbox": "PRESENT",
                  "door": "%s",
                  "lock": "%s",
                  "extensionBoard": "ONLINE",
                  "printer": "%s"
                }
                """
                .formatted(door, lock, printer);
    }
}
