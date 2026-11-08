package com.francescopampallona.chatroom.controller;

import com.francescopampallona.chatroom.dto.request.MessageRequest;
import com.francescopampallona.chatroom.dto.response.MessageResponse;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageService messageService;

    @MessageMapping("/rooms/{roomId}/messages")
    public MessageResponse sendMessage(
            @DestinationVariable Long roomId,
            @Payload MessageRequest request,
            Authentication authentication
    ) {

        return messageService.sendMessage(
                roomId,
                request,
                authentication
        );
    }
}