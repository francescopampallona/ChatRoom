package com.francescopampallona.chatroom.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/agents")
@RequiredArgsConstructor
public class AiAgentController {
    private final AiProperties properties;
    private final AiRoomData data;

    @GetMapping
    public Catalog catalog() {
        return new Catalog(properties.isEnabled(), data.availableAgents());
    }

    public record Catalog(boolean enabled, List<AiRoomStatus.Agent> agents) { }
}
