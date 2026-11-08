package com.francescopampallona.chatroom.controller;

import com.francescopampallona.chatroom.dto.response.MessageResponse;
import com.francescopampallona.chatroom.dto.response.PageResponse;
import com.francescopampallona.chatroom.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<PageResponse<MessageResponse>> getMessageHistory(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                messageService.getMessageHistory(roomId, page, size)
        );
    }
}