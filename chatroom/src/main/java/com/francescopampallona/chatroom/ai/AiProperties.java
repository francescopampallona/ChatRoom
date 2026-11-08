package com.francescopampallona.chatroom.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chatroom.ai")
public class AiProperties {
    private boolean enabled = true;
    private String baseUrl = "http://localhost:11434";
    private String model = "llama3.2:3b";
    private int intervalSeconds = 15;
    private int maxAutonomousTurns = 6;
    private int requestTimeoutSeconds = 120;
}
