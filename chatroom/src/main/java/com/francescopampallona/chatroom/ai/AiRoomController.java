package com.francescopampallona.chatroom.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.francescopampallona.chatroom.model.User;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{roomId}/ai")
@RequiredArgsConstructor
public class AiRoomController {
    private final AiRoomData data;
    private final AiCoordinator coordinator;

    @GetMapping
    public AiRoomStatus status(@PathVariable Long roomId, Authentication authentication) {
        data.requireAccess(roomId, authentication);
        return roomStatus(roomId, authentication);
    }

    @PostMapping
    public AiRoomStatus setRunning(@PathVariable Long roomId, @Valid @RequestBody Control request,
                                   Authentication authentication) {
        data.requireAccess(roomId, authentication);
        coordinator.setRunning(roomId, request.running());
        return roomStatus(roomId, authentication);
    }

    @PostMapping("/turn")
    public AiRoomStatus singleTurn(@PathVariable Long roomId, Authentication authentication) {
        data.requireAccess(roomId, authentication);
        coordinator.singleTurn(roomId);
        return roomStatus(roomId, authentication);
    }

    @PutMapping("/agents")
    public AiRoomStatus configureAgents(@PathVariable Long roomId, @Valid @RequestBody Selection request,
                                       Authentication authentication) {
        data.requireAccess(roomId, authentication);
        if (authentication == null || !(authentication.getPrincipal() instanceof User owner)) {
            throw new AccessDeniedException("Solo il proprietario puo scegliere gli agenti");
        }
        coordinator.configureAgents(roomId, owner, request.agentKeys());
        return roomStatus(roomId, authentication);
    }

    private AiRoomStatus roomStatus(Long roomId, Authentication authentication) {
        return coordinator.status(roomId).withCanManageAgents(data.canManageAgents(roomId, authentication));
    }

    @ExceptionHandler(AiBusyException.class)
    public ResponseEntity<String> busy(AiBusyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    public record Control(@NotNull Boolean running) { }
    public record Selection(@NotNull @Size(max = 3) List<@NotBlank String> agentKeys) { }
}
