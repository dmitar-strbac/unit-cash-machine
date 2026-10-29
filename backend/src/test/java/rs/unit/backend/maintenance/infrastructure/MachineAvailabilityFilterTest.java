package rs.unit.backend.maintenance.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import rs.unit.backend.maintenance.application.MachineRuntimeGuard;
import rs.unit.backend.maintenance.application.MachineRuntimeView;
import rs.unit.backend.maintenance.domain.MachineRunState;

class MachineAvailabilityFilterTest {

    private final MachineRuntimeGuard runtime = mock(MachineRuntimeGuard.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final MachineAvailabilityFilter filter = new MachineAvailabilityFilter(runtime);

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/",
                "/assets/logo.svg",
                "/api/machine/status",
                "/api/machine/runtime",
                "/api/auth/csrf",
                "/api/simulator/power-on",
                "/api/simulator/start-application"
            })
    void publicAndNonApiPathsDoNotRequireRunningApplication(String path) throws Exception {
        var request = new MockHttpServletRequest("GET", "/unit" + path);
        request.setContextPath("/unit");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(runtime);
    }

    @ParameterizedTest
    @CsvSource({
        "GET, /api/maintenance/components",
        "POST, /api/auth/activity",
        "POST, /api/auth/logout",
        "POST, /api/maintenance/commands"
    })
    void runningApplicationAllowsRequests(String method, String path) throws Exception {
        givenState(MachineRunState.RUNNING);

        var request = new MockHttpServletRequest(method, path);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @CsvSource({
        "GET, /api/maintenance/components",
        "POST, /api/auth/activity",
        "POST, /api/auth/logout"
    })
    void resetAllowsReadsActivityAndLogout(String method, String path) throws Exception {
        givenState(MachineRunState.RESETTING);

        var request = new MockHttpServletRequest(method, path);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @EnumSource(
            value = MachineRunState.class,
            names = {"RESETTING", "REBOOTING", "SHUT_DOWN", "APPLICATION_STOPPED"})
    void unavailableApplicationBlocksStateChangingRequests(MachineRunState state) throws Exception {
        givenState(state);

        var request = new MockHttpServletRequest("POST", "/api/maintenance/commands");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertEquals("2", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("\"status\":503"));
        assertTrue(response.getContentAsString().contains("Machine unavailable"));
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @EnumSource(
            value = MachineRunState.class,
            names = {"REBOOTING", "SHUT_DOWN", "APPLICATION_STOPPED"})
    void stoppedApplicationAlsoBlocksProtectedReads(MachineRunState state) throws Exception {
        givenState(state);

        var request = new MockHttpServletRequest("GET", "/api/maintenance/components");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
    }

    private void givenState(MachineRunState state) {
        when(runtime.view()).thenReturn(new MachineRuntimeView(state, 0, null, null, null, null));
    }
}
