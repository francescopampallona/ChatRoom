package com.francescopampallona.chatroom.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.francescopampallona.chatroom.model.User;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiCoordinatorTest {
    private static final long ROOM = 42L;
    private static final long AGENT = 101L;
    private AiProperties properties;
    private OllamaClient ollama;
    private AiRoomData data;
    private QueuedExecutor worker;
    private AtomicLong now;
    private AiCoordinator coordinator;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        ollama = mock(OllamaClient.class);
        data = mock(AiRoomData.class);
        worker = new QueuedExecutor();
        now = new AtomicLong(1_000);
        coordinator = new AiCoordinator(properties, ollama, data, worker, now::get);
        when(ollama.readiness()).thenReturn(new OllamaClient.Readiness(true, null));
        when(data.agents(anyLong())).thenReturn(List.of());
        when(data.personas(anyLong())).thenReturn(AiPersona.ALL);
        when(data.context(anyLong(), any(AiPersona.class))).thenAnswer(invocation ->
                new AiRoomData.Context(AGENT, "Room " + invocation.getArgument(0),
                        List.of(new OllamaClient.ChatMessage("user", "Ciao"))));
        when(ollama.generate(anyString(), anyList())).thenReturn("Risposta AI");
    }

    @Test
    void sixAutonomousTurnsLeaveTheRoomListeningWithoutFurtherGeneration() {
        coordinator.setRunning(ROOM, true);
        for (int turn = 0; turn < 6; turn++) {
            coordinator.tick();
            assertEquals(1, worker.size());
            assertTrue(coordinator.status(ROOM).generating());
            worker.runNext();
            now.addAndGet(15_000);
        }

        AiRoomStatus status = coordinator.status(ROOM);
        assertTrue(status.running());
        assertFalse(status.generating());
        assertEquals(0, status.remainingTurns());
        coordinator.tick();
        now.addAndGet(1_000_000);
        coordinator.tick();
        assertEquals(0, worker.size());
        verify(ollama, times(6)).generate(anyString(), anyList());
        verify(data, times(6)).saveReply(ROOM, AGENT, "Risposta AI");

        ArgumentCaptor<AiPersona> personas = ArgumentCaptor.forClass(AiPersona.class);
        verify(data, times(6)).context(eq(ROOM), personas.capture());
        assertEquals(List.of("aria", "bruno", "mira", "aria", "bruno", "mira"),
                personas.getAllValues().stream().map(AiPersona::key).toList());
    }

    @Test
    void intervalIsMeasuredFromTheCompletedReply() {
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        now.set(8_000);
        worker.runNext();
        now.set(22_999);
        coordinator.tick();
        assertEquals(0, worker.size());
        now.set(23_000);
        coordinator.tick();
        assertEquals(1, worker.size());
    }

    @Test
    void humanMessageResetsBoundedTurnsAndSchedulesAfterTwoSeconds() {
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();
        assertEquals(5, coordinator.status(ROOM).remainingTurns());

        now.set(2_000);
        coordinator.onHumanMessage(ROOM);
        assertEquals(6, coordinator.status(ROOM).remainingTurns());
        now.set(3_999);
        coordinator.tick();
        assertEquals(0, worker.size());
        now.set(4_000);
        coordinator.tick();
        assertEquals(1, worker.size());
        worker.runNext();
        assertEquals(5, coordinator.status(ROOM).remainingTurns());
    }

    @Test
    void humanMessageReactivatesListeningAfterBudgetIsExhausted() {
        properties.setMaxAutonomousTurns(1);
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();
        assertTrue(coordinator.status(ROOM).running());
        assertEquals(0, coordinator.status(ROOM).remainingTurns());

        coordinator.onHumanMessage(ROOM);
        now.addAndGet(2_000);
        coordinator.tick();
        worker.runNext();

        verify(data, times(2)).saveReply(ROOM, AGENT, "Risposta AI");
        assertTrue(coordinator.status(ROOM).running());
        assertEquals(0, coordinator.status(ROOM).remainingTurns());
    }

    @Test
    void startingAnExhaustedListeningRoomExplicitlyRearmsItsBudget() {
        properties.setMaxAutonomousTurns(1);
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();
        assertTrue(coordinator.status(ROOM).running());
        assertEquals(0, coordinator.status(ROOM).remainingTurns());

        coordinator.setRunning(ROOM, true);

        assertEquals(1, coordinator.status(ROOM).remainingTurns());
        coordinator.tick();
        worker.runNext();
        verify(data, times(2)).saveReply(ROOM, AGENT, "Risposta AI");
    }

    @Test
    void repeatedStartWhileBudgetRemainsDoesNotRefillOrBypassTheInterval() {
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();
        assertEquals(5, coordinator.status(ROOM).remainingTurns());

        coordinator.setRunning(ROOM, true);
        coordinator.tick();

        assertEquals(5, coordinator.status(ROOM).remainingTurns());
        assertEquals(0, worker.size());
        verify(ollama, times(1)).generate(anyString(), anyList());
    }

    @Test
    void humanMessagesDoNotStartPausedOrUnknownRooms() {
        coordinator.onHumanMessage(ROOM);
        coordinator.tick();
        assertFalse(coordinator.status(ROOM).running());
        assertEquals(0, worker.size());

        coordinator.setRunning(ROOM, true);
        coordinator.setRunning(ROOM, false);
        coordinator.onHumanMessage(ROOM);
        now.addAndGet(100_000);
        coordinator.tick();
        assertFalse(coordinator.status(ROOM).running());
        assertEquals(0, coordinator.status(ROOM).remainingTurns());
        assertEquals(0, worker.size());
        verify(ollama, never()).generate(anyString(), anyList());
    }

    @Test
    void humanMessagesInOtherRoomsDoNotChangeThisRoomsBudgetOrSchedule() {
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();
        coordinator.onHumanMessage(ROOM + 1);
        now.addAndGet(2_000);
        coordinator.tick();

        assertEquals(5, coordinator.status(ROOM).remainingTurns());
        assertEquals(0, worker.size());
        verify(data, times(1)).context(eq(ROOM), any(AiPersona.class));
        verify(data, never()).context(eq(ROOM + 1), any(AiPersona.class));
    }

    @Test
    void manualTurnWhilePausedDoesNotStartOrConsumeAutonomousTurns() {
        coordinator.singleTurn(ROOM);
        assertTrue(coordinator.status(ROOM).generating());
        assertFalse(coordinator.status(ROOM).running());
        worker.runNext();

        assertFalse(coordinator.status(ROOM).running());
        assertFalse(coordinator.status(ROOM).generating());
        assertEquals(0, coordinator.status(ROOM).remainingTurns());
        now.addAndGet(100_000);
        coordinator.tick();
        assertEquals(0, worker.size());
        verify(data).saveReply(ROOM, AGENT, "Risposta AI");
    }

    @Test
    void manualTurnWhileRunningPreservesTheAutonomousBudget() {
        coordinator.setRunning(ROOM, true);
        coordinator.singleTurn(ROOM);
        worker.runNext();

        assertTrue(coordinator.status(ROOM).running());
        assertEquals(6, coordinator.status(ROOM).remainingTurns());
    }

    @Test
    void globallyOnlyOneRequestMayBeQueuedOrInFlight() {
        coordinator.setRunning(ROOM, true);
        coordinator.setRunning(ROOM + 1, true);
        coordinator.tick();
        coordinator.tick();
        assertEquals(1, worker.size());
        assertThrows(IllegalStateException.class, () -> coordinator.singleTurn(ROOM + 1));
        worker.runNext();
        coordinator.tick();
        assertEquals(1, worker.size());
        worker.runNext();
        verify(ollama, times(2)).generate(anyString(), anyList());
    }

    @Test
    void pausingDuringInferenceDiscardsTheLateReply() {
        when(ollama.generate(anyString(), anyList())).thenAnswer(invocation -> {
            coordinator.setRunning(ROOM, false);
            assertFalse(coordinator.status(ROOM).generating());
            return "Risposta vecchia";
        });
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();

        verify(data, never()).saveReply(anyLong(), anyLong(), anyString());
        assertFalse(coordinator.status(ROOM).running());
        assertEquals(0, coordinator.status(ROOM).remainingTurns());
    }

    @Test
    void pauseAndRestartDuringInferenceCannotPublishTheOldEpoch() {
        when(ollama.generate(anyString(), anyList())).thenAnswer(invocation -> {
            coordinator.setRunning(ROOM, false);
            coordinator.setRunning(ROOM, true);
            return "Risposta vecchia";
        }).thenReturn("Risposta nuova");
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();

        verify(data, never()).saveReply(anyLong(), anyLong(), anyString());
        assertTrue(coordinator.status(ROOM).running());
        assertEquals(6, coordinator.status(ROOM).remainingTurns());
        coordinator.tick();
        worker.runNext();
        verify(data).saveReply(ROOM, AGENT, "Risposta nuova");
        assertEquals(5, coordinator.status(ROOM).remainingTurns());
    }

    @Test
    void newHumanMessageDuringInferenceDiscardsOldContextAndRestartsAfterDelay() {
        when(ollama.generate(anyString(), anyList())).thenAnswer(invocation -> {
            coordinator.onHumanMessage(ROOM);
            return "Risposta vecchia";
        }).thenReturn("Risposta al nuovo messaggio");
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();

        verify(data, never()).saveReply(anyLong(), anyLong(), anyString());
        assertEquals(6, coordinator.status(ROOM).remainingTurns());
        now.set(2_999);
        coordinator.tick();
        assertEquals(0, worker.size());
        now.set(3_000);
        coordinator.tick();
        worker.runNext();
        verify(data).saveReply(ROOM, AGENT, "Risposta al nuovo messaggio");
    }

    @Test
    void threeErrorsBackOffThenPauseWithoutPublishingFallbackMessages() {
        when(ollama.generate(anyString(), anyList()))
                .thenThrow(new OllamaClient.OllamaException("Ollama non e raggiungibile."));
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();
        assertTrue(coordinator.status(ROOM).running());
        assertEquals(6, coordinator.status(ROOM).remainingTurns());
        now.set(30_999);
        coordinator.tick();
        assertEquals(0, worker.size());
        now.set(31_000);
        coordinator.tick();
        worker.runNext();
        now.set(90_999);
        coordinator.tick();
        assertEquals(0, worker.size());
        now.set(91_000);
        coordinator.tick();
        worker.runNext();

        AiRoomStatus status = coordinator.status(ROOM);
        assertFalse(status.running());
        assertEquals(0, status.remainingTurns());
        assertTrue(status.error().contains("tre tentativi"));
        assertTrue(status.error().contains("Ollama"));
        now.addAndGet(1_000_000);
        coordinator.tick();
        assertEquals(0, worker.size());
        verify(ollama, times(3)).generate(anyString(), anyList());
        verify(data, never()).saveReply(anyLong(), anyLong(), anyString());
    }

    @Test
    void failedSaveKeepsBudgetAndReportsErrorWithoutAnotherMessage() {
        doThrow(new IllegalStateException("Database unavailable"))
                .when(data).saveReply(ROOM, AGENT, "Risposta AI");
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();

        assertTrue(coordinator.status(ROOM).running());
        assertEquals(6, coordinator.status(ROOM).remainingTurns());
        assertNotNull(coordinator.status(ROOM).error());
        assertFalse(coordinator.status(ROOM).error().contains("Database unavailable"));
        verify(data, times(1)).saveReply(anyLong(), anyLong(), anyString());
        verify(ollama, times(1)).generate(anyString(), anyList());
    }

    @Test
    void manualFailureWhilePausedDoesNotScheduleRetriesOrStartTheRoom() {
        when(ollama.generate(anyString(), anyList()))
                .thenThrow(new OllamaClient.OllamaException("Modello non disponibile"));
        coordinator.singleTurn(ROOM);
        worker.runNext();
        now.addAndGet(1_000_000);
        coordinator.tick();

        assertFalse(coordinator.status(ROOM).running());
        assertEquals(0, worker.size());
        assertNotNull(coordinator.status(ROOM).error());
        verify(ollama, times(1)).generate(anyString(), anyList());
        verify(data, never()).saveReply(anyLong(), anyLong(), anyString());
    }

    @Test
    void disabledAiRejectsControlAndIgnoresHumanMessagesAndTicks() {
        properties.setEnabled(false);
        var present = List.of(new AiRoomStatus.Agent("mira", AGENT, "mira.ai", "Mira", "Pratica"));
        when(data.agents(ROOM)).thenReturn(present);
        coordinator.onHumanMessage(ROOM);
        coordinator.tick();

        assertThrows(IllegalStateException.class, () -> coordinator.setRunning(ROOM, true));
        assertThrows(IllegalStateException.class, () -> coordinator.singleTurn(ROOM));
        assertFalse(coordinator.status(ROOM).enabled());
        assertFalse(coordinator.status(ROOM).ready());
        assertEquals(present, coordinator.status(ROOM).agents());
        assertEquals(0, worker.size());
        verifyNoInteractions(ollama);
        verify(data, never()).context(anyLong(), any());
        verify(data, never()).personas(anyLong());
    }

    @Test
    void closingDuringInferencePreventsLatePersistenceAndFurtherWork() {
        when(ollama.generate(anyString(), anyList())).thenAnswer(invocation -> {
            coordinator.close();
            return "Risposta vecchia";
        });
        coordinator.setRunning(ROOM, true);
        coordinator.tick();
        worker.runNext();
        coordinator.tick();

        verify(data, never()).saveReply(anyLong(), anyLong(), anyString());
        assertEquals(0, worker.size());
        assertFalse(coordinator.status(ROOM).running());
        assertThrows(IllegalStateException.class, () -> coordinator.singleTurn(ROOM));
    }

    @Test
    void onlySelectedAgentsTakeTurnsAndOneAgentCanReplyAlone() {
        when(data.personas(ROOM)).thenReturn(List.of(AiPersona.ALL.get(1), AiPersona.ALL.get(2)));
        for (int i = 0; i < 3; i++) {
            coordinator.singleTurn(ROOM);
            worker.runNext();
        }
        when(data.personas(ROOM + 1)).thenReturn(List.of(AiPersona.ALL.get(2)));
        coordinator.singleTurn(ROOM + 1);
        worker.runNext();
        coordinator.singleTurn(ROOM + 1);
        worker.runNext();

        ArgumentCaptor<AiPersona> selected = ArgumentCaptor.forClass(AiPersona.class);
        verify(data, times(3)).context(eq(ROOM), selected.capture());
        assertEquals(List.of("bruno", "mira", "bruno"), selected.getAllValues().stream().map(AiPersona::key).toList());
        verify(data, times(2)).context(ROOM + 1, AiPersona.ALL.get(2));
    }

    @Test
    void emptyRoomRejectsStartAndManualTurnWithoutConsumingTheGlobalWorker() {
        when(data.personas(ROOM)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> coordinator.setRunning(ROOM, true));
        assertThrows(IllegalStateException.class, () -> coordinator.singleTurn(ROOM));
        coordinator.singleTurn(ROOM + 1);
        worker.runNext();
        verify(data, never()).context(eq(ROOM), any());
        verify(data).saveReply(ROOM + 1, AGENT, "Risposta AI");
    }

    @Test
    void membershipChangeDuringInferencePausesOnlyThisRoomAndDiscardsItsReply() {
        User owner = User.builder().id(8L).build();
        when(data.configureAgents(ROOM, owner, List.of("mira"))).thenReturn(true);
        coordinator.setRunning(ROOM, true);
        coordinator.setRunning(ROOM + 1, true);
        when(ollama.generate(anyString(), anyList())).thenAnswer(invocation -> {
            coordinator.configureAgents(ROOM, owner, List.of("mira"));
            when(data.personas(ROOM)).thenReturn(List.of(AiPersona.ALL.get(2)));
            return "Risposta con i vecchi partecipanti";
        }).thenReturn("Risposta nuova");
        coordinator.singleTurn(ROOM);
        worker.runNext();

        verify(data, never()).saveReply(eq(ROOM), anyLong(), anyString());
        assertFalse(coordinator.status(ROOM).running());
        assertFalse(coordinator.status(ROOM).generating());
        assertEquals(0, coordinator.status(ROOM).remainingTurns());
        assertTrue(coordinator.status(ROOM + 1).running());
        coordinator.singleTurn(ROOM);
        worker.runNext();
        verify(data).context(ROOM, AiPersona.ALL.get(2));
        verify(data).saveReply(ROOM, AGENT, "Risposta nuova");
    }

    @Test
    void failedOrUnchangedSelectionDoesNotInterruptAnActiveConversation() {
        User owner = User.builder().id(8L).build();
        coordinator.setRunning(ROOM, true);
        doThrow(new IllegalArgumentException("Agente sconosciuto"))
                .when(data).configureAgents(ROOM, owner, List.of("unknown"));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.configureAgents(ROOM, owner, List.of("unknown")));
        coordinator.configureAgents(ROOM, owner, List.of("aria", "bruno", "mira"));
        assertTrue(coordinator.status(ROOM).running());
        assertEquals(6, coordinator.status(ROOM).remainingTurns());
    }

    @Test
    void oldestDueRoomGetsItsTurnEvenWhenGenerationTakesLongerThanTheInterval() {
        for (long room = ROOM; room < ROOM + 3; room++) coordinator.setRunning(room, true);
        for (int turn = 0; turn < 6; turn++) {
            coordinator.tick();
            now.addAndGet(30_000);
            worker.runNext();
        }
        for (long room = ROOM; room < ROOM + 3; room++) {
            verify(data, times(2)).saveReply(room, AGENT, "Risposta AI");
        }
    }

    @Test
    void removingAgentsBeforeQueuedWorkBeginsSkipsTheModelCall() {
        User owner = User.builder().id(8L).build();
        when(data.configureAgents(ROOM, owner, List.of())).thenReturn(true);
        coordinator.singleTurn(ROOM);
        coordinator.configureAgents(ROOM, owner, List.of());
        worker.runNext();
        verify(ollama, never()).generate(anyString(), anyList());
        verify(data, never()).saveReply(anyLong(), anyLong(), anyString());
        assertFalse(coordinator.status(ROOM).generating());
    }

    private static class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int size() {
            return tasks.size();
        }

        void runNext() {
            Runnable task = tasks.poll();
            assertNotNull(task, "Expected a queued inference task");
            task.run();
        }
    }
}
