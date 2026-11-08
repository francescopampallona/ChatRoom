package com.francescopampallona.chatroom.ai;

import jakarta.annotation.PreDestroy;
import com.francescopampallona.chatroom.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/** One model request at a time for this application instance; no transaction spans inference. */
@Component
public class AiCoordinator {
    private final AiProperties properties;
    private final OllamaClient ollama;
    private final AiRoomData data;
    private final Executor worker;
    private final LongSupplier clock;
    private final Map<Long, State> states = new HashMap<>();
    private boolean busy;
    private boolean closed;

    @Autowired
    public AiCoordinator(AiProperties properties, OllamaClient ollama, AiRoomData data) {
        this(properties, ollama, data, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "chatroom-ai-inference");
            thread.setDaemon(true);
            return thread;
        }), System::currentTimeMillis);
    }

    AiCoordinator(AiProperties properties, OllamaClient ollama, AiRoomData data,
                  Executor worker, LongSupplier clock) {
        this.properties = properties;
        this.ollama = ollama;
        this.data = data;
        this.worker = worker;
        this.clock = clock;
    }

    public AiRoomStatus status(Long roomId) {
        // Network readiness checks are cached by the client and never hold the coordinator lock.
        OllamaClient.Readiness readiness = properties.isEnabled()
                ? ollama.readiness() : new OllamaClient.Readiness(false, "Gli agenti AI sono disabilitati");
        synchronized (this) {
            var agents = data.agents(roomId);
            State state = states.getOrDefault(roomId, new State());
            return new AiRoomStatus(properties.isEnabled(), readiness.ready(), state.running,
                    state.generating && state.generationEpoch == state.epoch, state.remainingTurns,
                    properties.getModel(), state.error != null ? state.error : readiness.error(), agents, false);
        }
    }

    /** The membership transaction and its commit cannot interleave with a reply's final save. */
    public synchronized void configureAgents(Long roomId, User owner, List<String> agentKeys) {
        if (closed) throw new IllegalStateException("Gli agenti AI sono disabilitati");
        if (data.configureAgents(roomId, owner, agentKeys)) {
            State state = states.computeIfAbsent(roomId, key -> new State());
            pause(state);
            state.nextAgent = 0;
        }
    }

    public synchronized void setRunning(Long roomId, boolean running) {
        requireEnabled();
        if (running) requireParticipants(roomId);
        State state = states.computeIfAbsent(roomId, key -> new State());
        if (!running) {
            pause(state);
            return;
        }
        if (!state.running || state.remainingTurns == 0) {
            state.epoch++;
            state.running = true;
            state.remainingTurns = turnLimit();
            state.nextAt = clock.getAsLong();
            state.error = null;
            state.failures = 0;
        }
    }

    public synchronized void singleTurn(Long roomId) {
        requireEnabled();
        if (busy) throw new AiBusyException();
        State state = states.computeIfAbsent(roomId, key -> new State());
        state.error = null;
        launch(roomId, state, true);
    }

    /** Called exclusively by the AFTER_COMMIT listener, never before a human message is durable. */
    public synchronized void onHumanMessage(Long roomId) {
        if (!properties.isEnabled() || closed) return;
        State state = states.get(roomId);
        if (state == null || !state.running) return;
        // An answer based on an older context must not overtake this new human message.
        state.epoch++;
        state.remainingTurns = turnLimit();
        state.nextAt = clock.getAsLong() + 2_000;
        state.failures = 0;
        state.error = null;
    }

    @Scheduled(fixedDelay = 1000)
    public synchronized void tick() {
        if (closed || busy || !properties.isEnabled()) return;
        long now = clock.getAsLong();
        // Oldest due room goes first, so slow inference cannot starve the other rooms.
        var due = states.entrySet().stream()
                .filter(entry -> entry.getValue().running && entry.getValue().remainingTurns > 0
                        && entry.getValue().nextAt <= now)
                .min(Comparator.<Map.Entry<Long, State>>comparingLong(entry -> entry.getValue().nextAt)
                        .thenComparingLong(Map.Entry::getKey));
        if (due.isPresent()) {
            State state = due.get().getValue();
            try {
                launch(due.get().getKey(), state, false);
            } catch (RuntimeException exception) {
                pause(state);
                state.error = "Non e stato possibile avviare gli agenti della stanza. Riprova.";
            }
        }
    }

    private void launch(Long roomId, State state, boolean manual) {
        List<AiPersona> participants = requireParticipants(roomId);
        AiPersona persona = participants.get(state.nextAgent % participants.size());
        busy = true;
        state.generating = true;
        state.generationEpoch = state.epoch;
        long epoch = state.epoch;
        try {
            worker.execute(() -> generate(roomId, state, epoch, persona, participants.size(), manual));
        } catch (RuntimeException exception) {
            busy = false;
            state.generating = false;
            throw exception;
        }
    }

    private void generate(Long roomId, State state, long epoch, AiPersona persona, int participantCount, boolean manual) {
        try {
            synchronized (this) {
                if (closed || epoch != state.epoch) return;
            }
            AiRoomData.Context context = data.context(roomId, persona);
            synchronized (this) {
                if (closed || epoch != state.epoch) return;
            }
            String reply = ollama.generate(context.systemPrompt(), context.messages());
            synchronized (this) {
                if (closed || epoch != state.epoch) return;
                // Keep the final epoch check, database commit and broadcast serialized with pause.
                // The transactional proxy commits before returning; no model call holds this lock.
                data.saveReply(roomId, context.agentId(), reply);
                state.nextAgent = (state.nextAgent + 1) % participantCount;
                if (!manual) state.remainingTurns = Math.max(0, state.remainingTurns - 1);
                state.nextAt = clock.getAsLong() + intervalMillis();
                state.failures = 0;
                state.error = null;
            }
        } catch (RuntimeException exception) {
            synchronized (this) {
                if (closed || epoch != state.epoch) return;
                state.failures++;
                state.error = exception instanceof OllamaClient.OllamaException
                        ? exception.getMessage() : "Non e stato possibile completare la risposta AI. Riprova tra poco.";
                state.nextAt = clock.getAsLong() + Math.min(60_000L, intervalMillis() * (1L << Math.min(state.failures, 3)));
                if (!manual && state.failures >= 3) {
                    state.running = false;
                    state.remainingTurns = 0;
                    state.error = "Agenti in pausa dopo tre tentativi non riusciti. " + state.error;
                }
            }
        } finally {
            synchronized (this) {
                state.generating = false;
                busy = false;
            }
        }
    }

    private int turnLimit() {
        return Math.max(1, Math.min(properties.getMaxAutonomousTurns(), 30));
    }

    private List<AiPersona> requireParticipants(Long roomId) {
        List<AiPersona> participants = data.personas(roomId);
        if (participants.isEmpty()) throw new IllegalStateException("Aggiungi almeno un agente AI alla stanza");
        return participants;
    }

    private void pause(State state) {
        state.epoch++;
        state.running = false;
        state.remainingTurns = 0;
        state.error = null;
        state.failures = 0;
    }

    private long intervalMillis() {
        return Math.max(1, properties.getIntervalSeconds()) * 1000L;
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || closed) throw new IllegalStateException("Gli agenti AI sono disabilitati");
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        states.values().forEach(state -> {
            state.epoch++;
            state.running = false;
        });
        if (worker instanceof ExecutorService executor) executor.shutdownNow();
    }

    private static class State {
        boolean running;
        boolean generating;
        int remainingTurns;
        int nextAgent;
        int failures;
        long epoch;
        long generationEpoch;
        long nextAt;
        String error;
    }
}
