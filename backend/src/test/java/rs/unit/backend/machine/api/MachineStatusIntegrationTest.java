package rs.unit.backend.machine.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfiguration;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfigurationRepository;
import rs.unit.backend.support.MachineTestState;

@SpringBootTest(
        properties = {
            "unit.collection.monitor.enabled=false",
            "unit.deposit.monitor.enabled=false"
        })
@Transactional
class MachineStatusIntegrationTest {

    @Autowired private WebApplicationContext applicationContext;

    @Autowired private MachineConfigurationRepository configurationRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MachineTestState.reset(jdbcTemplate);

        mockMvc =
                MockMvcBuilders.webAppContextSetup(applicationContext)
                        .apply(springSecurity())
                        .build();
    }

    @Test
    void returnsStoredConfigurationAndInitialHardwareStatus() throws Exception {
        MachineConfiguration configuration =
                configurationRepository.findById((short) 1).orElseThrow();

        assertEquals(Short.valueOf((short) 1), configuration.getId());

        mockMvc.perform(get("/api/machine/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.machineCode").value(configuration.getMachineCode()))
                .andExpect(jsonPath("$.displayName").value(configuration.getDisplayName()))
                .andExpect(jsonPath("$.location").value(configuration.getLocation()))
                .andExpect(jsonPath("$.currency").value(configuration.getCurrency()))
                .andExpect(jsonPath("$.timeZone").value(configuration.getTimeZone()))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.secure").value(true))
                .andExpect(jsonPath("$.depositHardwareReady").value(true))
                .andExpect(jsonPath("$.printingAvailable").value(true))
                .andExpect(jsonPath("$.hardware.acceptor").value("READY"))
                .andExpect(jsonPath("$.hardware.cashbox").value("PRESENT"))
                .andExpect(jsonPath("$.hardware.door").value("CLOSED"))
                .andExpect(jsonPath("$.hardware.lock").value("LOCKED"))
                .andExpect(jsonPath("$.hardware.extensionBoard").value("ONLINE"))
                .andExpect(jsonPath("$.hardware.printer").value("READY"))
                .andExpect(jsonPath("$.indicator").value("GREEN"))
                .andExpect(jsonPath("$.alertCount").value(0))
                .andExpect(jsonPath("$.alerts").isEmpty());
    }
}
