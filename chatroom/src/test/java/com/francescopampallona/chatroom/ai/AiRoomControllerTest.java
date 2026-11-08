package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.controller.GlobalExceptionHandler;
import com.francescopampallona.chatroom.model.User;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiRoomControllerTest {
    private AiRoomData data;
    private AiCoordinator coordinator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        data = mock(AiRoomData.class);
        coordinator = mock(AiCoordinator.class);
        mvc = MockMvcBuilders.standaloneSetup(new AiRoomController(data, coordinator))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void occupiedInferenceReturnsConflictEvenWithTheGlobalRuntimeExceptionHandler() throws Exception {
        doThrow(new AiBusyException()).when(coordinator).singleTurn(7L);

        mvc.perform(post("/api/rooms/7/ai/turn"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Un agente sta gia rispondendo. Riprova tra poco."));

        verify(data).requireAccess(7L, null);
        verify(coordinator).singleTurn(7L);
    }

    @Test
    void unrelatedIllegalStateStillUsesTheExistingBadRequestMapping() throws Exception {
        doThrow(new IllegalStateException("Gli agenti AI sono disabilitati"))
                .when(coordinator).singleTurn(7L);

        mvc.perform(post("/api/rooms/7/ai/turn"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Gli agenti AI sono disabilitati"));
    }

    @Test
    void ownerCanSelectAgentsAndReceivesTheUpdatedRoomStatus() throws Exception {
        User owner = User.builder().id(11L).build();
        var authentication = new UsernamePasswordAuthenticationToken(owner, null, List.of());
        when(data.canManageAgents(7L, authentication)).thenReturn(true);
        when(coordinator.status(7L)).thenReturn(new AiRoomStatus(true, true, false, false, 0,
                "local", null, List.of(new AiRoomStatus.Agent("mira", 4L, "mira.ai", "Mira", "Pratica")), false));

        mvc.perform(put("/api/rooms/7/ai/agents").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agentKeys\":[\"mira\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManageAgents").value(true))
                .andExpect(jsonPath("$.agents[0].key").value("mira"))
                .andExpect(jsonPath("$.running").value(false));

        verify(data).requireAccess(7L, authentication);
        verify(coordinator).configureAgents(7L, owner, List.of("mira"));
    }

    @Test
    void missingOrOversizedSelectionDoesNotSilentlyRemoveAgents() throws Exception {
        for (String payload : List.of("{}", "{\"agentKeys\":null}",
                "{\"agentKeys\":[\"aria\",\"bruno\",\"mira\",\"aria\"]}", "{\"agentKeys\":[\"\"]}")) {
            mvc.perform(put("/api/rooms/7/ai/agents").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(coordinator);
    }

    @Test
    void nonOwnerMembershipDoesNotGrantPermissionToChangeAgents() throws Exception {
        User member = User.builder().id(12L).build();
        var authentication = new UsernamePasswordAuthenticationToken(member, null, List.of());
        doThrow(new AccessDeniedException("Solo il proprietario"))
                .when(coordinator).configureAgents(7L, member, List.of());

        mvc.perform(put("/api/rooms/7/ai/agents").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agentKeys\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonMemberCannotReadAThemedRoomsAgentStatus() throws Exception {
        doThrow(new AccessDeniedException("Non sei membro")).when(data).requireAccess(7L, null);
        mvc.perform(get("/api/rooms/7/ai")).andExpect(status().isForbidden());
        verifyNoInteractions(coordinator);
    }
}
