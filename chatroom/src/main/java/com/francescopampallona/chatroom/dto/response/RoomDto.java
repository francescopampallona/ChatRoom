package com.francescopampallona.chatroom.dto.response;

import com.francescopampallona.chatroom.enums.RoomType;
import com.francescopampallona.chatroom.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomDto {
    private Long id;
    private String name;
    private String description;
    private RoomType type;

    private Long ownerId;
    private String ownerUsername;

    private LocalDateTime createdAt;
}
