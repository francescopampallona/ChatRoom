package com.francescopampallona.chatroom.dto.request;

import com.francescopampallona.chatroom.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {
    @NotBlank(message = "Il nome della room è obbligatorio")
    @Size(max = 255, message = "Il nome della room non puo superare 255 caratteri")
    private String name;

    @Size(max = 255, message = "La descrizione non puo superare 255 caratteri")
    private String description;

    @NotNull(message = "Il tipo della room è obbligatorio")
    private RoomType type;

    @Size(max = 3, message = "Puoi scegliere al massimo tre agenti AI")
    private List<@NotBlank String> agentKeys;
}
