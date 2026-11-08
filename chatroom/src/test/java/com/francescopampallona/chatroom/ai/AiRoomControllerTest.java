package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
