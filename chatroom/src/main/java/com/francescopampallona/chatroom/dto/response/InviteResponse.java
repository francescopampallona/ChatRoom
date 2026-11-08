package com.francescopampallona.chatroom.dto.response;

import com.francescopampallona.chatroom.enums.InviteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteResponse {

    private Long id;

    private Long roomId;
    private String roomName;

    private Long invitedUserId;
    private String invitedUsername;

    private Long invitedByUserId;
    private String invitedByUsername;

    private InviteStatus status;

    private LocalDateTime createdAt;
}
