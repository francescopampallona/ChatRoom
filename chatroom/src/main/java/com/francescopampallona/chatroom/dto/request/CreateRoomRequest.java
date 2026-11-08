package com.francescopampallona.chatroom.dto.request;

import com.francescopampallona.chatroom.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {
    @NotBlank(message = "Il nome della room è obbligatorio")
    private String name;

    private String description;

    @NotNull(message = "Il tipo della room è obbligatorio")
    private RoomType type;
}
