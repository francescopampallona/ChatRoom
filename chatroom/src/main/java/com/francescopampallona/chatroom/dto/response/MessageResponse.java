package com.francescopampallona.chatroom.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long id;

    private Long roomId;

    private Long senderId;

    private String senderUsername;

    private String content;

    private LocalDateTime sentAt;
}