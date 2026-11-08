package com.francescopampallona.chatroom.ai;

import java.util.List;

public record AiRoomStatus(boolean enabled, boolean ready, boolean running, boolean generating,
                           int remainingTurns, String model, String error, List<Agent> agents,
                           boolean canManageAgents) {
    public AiRoomStatus withCanManageAgents(boolean allowed) {
        return new AiRoomStatus(enabled, ready, running, generating, remainingTurns, model, error, agents, allowed);
    }

    public record Agent(String key, Long id, String username, String displayName, String description) { }
}
