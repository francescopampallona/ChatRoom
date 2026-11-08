package com.francescopampallona.chatroom.controller;

import com.francescopampallona.chatroom.dto.request.CreateRoomRequest;
import com.francescopampallona.chatroom.dto.response.RoomDto;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.service.JwtService;
import com.francescopampallona.chatroom.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/room")
@CrossOrigin(origins = "http://localhost:4200")
public class RoomController {
    private final JwtService jwtService;
    private final RoomService roomService;

    public RoomController(JwtService jwtService, RoomService roomService){
        this.jwtService = jwtService;
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomDto> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        return ResponseEntity.ok(roomService.createRoom(request, user));
    }

    @GetMapping
    public ResponseEntity<List<RoomDto>> getAllRooms(
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                roomService.getAllRooms(user)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomDto> getRoomById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                roomService.getRoomById(id)
        );
    }

    //TODO: return paginated data
    @GetMapping("/public")
    public ResponseEntity<List<RoomDto>> getAllPublicRooms() {
        return ResponseEntity.ok(
                roomService.getAllPublicRooms()
        );
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<RoomDto> joinPublicRoom(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                roomService.joinPublicRoom(roomId, user)
        );
    }

    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        roomService.leaveRoom(roomId, user);

        return ResponseEntity.noContent().build();
    }

}
