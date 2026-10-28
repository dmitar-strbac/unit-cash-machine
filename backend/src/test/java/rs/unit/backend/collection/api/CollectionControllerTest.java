package rs.unit.backend.collection.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.collection.application.CollectionService;
import rs.unit.backend.collection.application.CollectionView;
import rs.unit.backend.collection.domain.CollectionStep;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.HardwareSnapshot.AcceptorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.CashboxState;
import rs.unit.backend.hardware.model.HardwareSnapshot.DoorState;
import rs.unit.backend.hardware.model.HardwareSnapshot.ExtensionBoardState;
import rs.unit.backend.hardware.model.HardwareSnapshot.LockState;
import rs.unit.backend.hardware.model.HardwareSnapshot.PrinterState;
import rs.unit.backend.hardware.model.PhysicalAction;
import rs.unit.backend.user.domain.UserRole;

@ExtendWith(MockitoExtension.class)
class CollectionControllerTest {

    @Mock private CollectionService service;

    private MockMvc mvc;
    private AuthenticatedUser cit;
    private UsernamePasswordAuthenticationToken authentication;
    private UUID collectionId;

    @BeforeEach
    void setUp() {
        collectionId = UUID.randomUUID();

        cit =
                new AuthenticatedUser(
                        UUID.randomUUID(),
                        "Test CIT",
                        UserRole.CIT,
                        Instant.parse("2026-01-01T12:15:00Z"));

        authentication = UsernamePasswordAuthenticationToken.authenticated(cit, null, List.of());

        SecurityContextHolder.getContext().setAuthentication(authentication);

        mvc =
                MockMvcBuilders.standaloneSetup(
                                new CollectionController(service),
                                new CollectionSimulationController(service))
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsCurrentCollection() throws Exception {
        when(service.current(cit)).thenReturn(view(CollectionStep.OPEN_DOOR));

        mvc.perform(get("/api/collections/current").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(collectionId.toString()))
                .andExpect(jsonPath("$.step").value("OPEN_DOOR"));

        verify(service).current(cit);
    }

    @Test
    void confirmsCassetteRemoval() throws Exception {
        when(service.confirmRemoval(cit, collectionId))
                .thenReturn(view(CollectionStep.INSERT_CASSETTE));

        mvc.perform(
                        post("/api/collections/{id}/confirm-removal", collectionId)
                                .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("INSERT_CASSETTE"));

        verify(service).confirmRemoval(cit, collectionId);
    }

    @Test
    void confirmsCassetteInsertion() throws Exception {
        when(service.confirmInsertion(cit, collectionId))
                .thenReturn(view(CollectionStep.CLOSE_DOOR));

        mvc.perform(
                        post("/api/collections/{id}/confirm-insertion", collectionId)
                                .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("CLOSE_DOOR"));

        verify(service).confirmInsertion(cit, collectionId);
    }

    @Test
    void restartInvalidatesHttpSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(
                        post("/api/collections/{id}/restart", collectionId)
                                .principal(authentication)
                                .session(session))
                .andExpect(status().isNoContent());

        verify(service).restart(cit, collectionId);
        assertTrue(session.isInvalid());
    }

    @Test
    void simulatesPhysicalAction() throws Exception {
        var hardware =
                new HardwareSnapshot(
                        AcceptorState.READY,
                        CashboxState.PRESENT,
                        DoorState.OPEN,
                        LockState.UNLOCKED,
                        ExtensionBoardState.ONLINE,
                        PrinterState.READY);

        when(service.simulate(cit, collectionId, PhysicalAction.OPEN_DOOR)).thenReturn(hardware);

        mvc.perform(
                        post("/api/collections/{id}/simulate", collectionId)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"action":"OPEN_DOOR"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.door").value("OPEN"));

        verify(service).simulate(cit, collectionId, PhysicalAction.OPEN_DOOR);
    }

    @Test
    void rejectsMissingSimulationAction() throws Exception {
        mvc.perform(
                        post("/api/collections/{id}/simulate", collectionId)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownSimulationAction() throws Exception {
        mvc.perform(
                        post("/api/collections/{id}/simulate", collectionId)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"action":"INVALID"}
                                        """))
                .andExpect(status().isBadRequest());
    }

    private CollectionView view(CollectionStep step) {
        return new CollectionView(
                collectionId,
                step,
                false,
                false,
                false,
                false,
                Instant.parse("2026-01-01T12:00:00Z"),
                null,
                null,
                null,
                null);
    }
}
