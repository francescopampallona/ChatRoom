package com.francescopampallona.chatroom.ai;

import java.util.List;

public record AiRoomStatus(boolean enabled, boolean ready, boolean running, boolean generating,
                           int remainingTurns, String model, String error, List<Agent> agents) {
    public record Agent(Long id, String username, String displayName, String description) { }
}
