package com.francescopampallona.chatroom.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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
            Thread thread = new Thread(task, "ai-lounge-inference");
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
        var agents = properties.isEnabled() ? data.agents() : java.util.List.<AiRoomStatus.Agent>of();
        synchronized (this) {
            State state = states.getOrDefault(roomId, new State());
            return new AiRoomStatus(properties.isEnabled(), readiness.ready(), state.running,
                    state.generating && state.generationEpoch == state.epoch, state.remainingTurns,
                    properties.getModel(), state.error != null ? state.error : readiness.error(), agents);
        }
    }

    public synchronized void setRunning(Long roomId, boolean running) {
        requireEnabled();
        State state = states.computeIfAbsent(roomId, key -> new State());
        if (!running) {
            state.epoch++;
            state.running = false;
            state.remainingTurns = 0;
            state.error = null;
            state.failures = 0;
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
        for (var entry : states.entrySet()) {
            State state = entry.getValue();
            if (state.running && state.remainingTurns > 0 && state.nextAt <= now) {
                launch(entry.getKey(), state, false);
                return;
            }
        }
    }

    private void launch(Long roomId, State state, boolean manual) {
        busy = true;
        state.generating = true;
        state.generationEpoch = state.epoch;
        long epoch = state.epoch;
        AiPersona persona = AiPersona.ALL.get(state.nextAgent % AiPersona.ALL.size());
        try {
            worker.execute(() -> generate(roomId, state, epoch, persona, manual));
        } catch (RuntimeException exception) {
            busy = false;
            state.generating = false;
            throw exception;
        }
    }

    private void generate(Long roomId, State state, long epoch, AiPersona persona, boolean manual) {
        try {
            AiRoomData.Context context = data.context(roomId, persona);
            String reply = ollama.generate(context.systemPrompt(), context.messages());
            synchronized (this) {
                if (closed || epoch != state.epoch) return;
                // Keep the final epoch check, database commit and broadcast serialized with pause.
                // The transactional proxy commits before returning; no model call holds this lock.
                data.saveReply(roomId, context.agentId(), reply);
                state.nextAgent = (state.nextAgent + 1) % AiPersona.ALL.size();
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
