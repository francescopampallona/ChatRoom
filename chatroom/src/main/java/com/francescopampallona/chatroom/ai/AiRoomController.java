package com.francescopampallona.chatroom.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        return coordinator.status(roomId);
    }

    @PostMapping
    public AiRoomStatus setRunning(@PathVariable Long roomId, @Valid @RequestBody Control request,
                                   Authentication authentication) {
        data.requireAccess(roomId, authentication);
        coordinator.setRunning(roomId, request.running());
        return coordinator.status(roomId);
    }

    @PostMapping("/turn")
    public AiRoomStatus singleTurn(@PathVariable Long roomId, Authentication authentication) {
        data.requireAccess(roomId, authentication);
        coordinator.singleTurn(roomId);
        return coordinator.status(roomId);
    }

    @ExceptionHandler(AiBusyException.class)
    public ResponseEntity<String> busy(AiBusyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    public record Control(@NotNull Boolean running) { }
}
