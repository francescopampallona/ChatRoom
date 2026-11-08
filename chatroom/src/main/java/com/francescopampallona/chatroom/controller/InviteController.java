package com.francescopampallona.chatroom.controller;

import com.francescopampallona.chatroom.dto.request.InviteUserRequest;
import com.francescopampallona.chatroom.dto.response.InviteResponse;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.service.InviteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invite")
@CrossOrigin(origins = "http://localhost:4200")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping("/{roomId}/invites")
    public ResponseEntity<InviteResponse> inviteUser(
            @PathVariable Long roomId,
            @Valid @RequestBody InviteUserRequest request,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();

        InviteResponse response = inviteService.inviteUserToPrivateRoom(
                roomId,
                request.getUsername(),
                currentUser
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<InviteResponse>> getMyInvites(
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                inviteService.getMyPendingInvites(currentUser)
        );
    }

    @PostMapping("/{inviteId}/accept")
    public ResponseEntity<InviteResponse> acceptInvite(
            @PathVariable Long inviteId,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                inviteService.acceptInvite(inviteId, currentUser)
        );
    }

    @PostMapping("/{inviteId}/decline")
    public ResponseEntity<InviteResponse> declineInvite(
            @PathVariable Long inviteId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                inviteService.declineInvite(inviteId)
        );
    }
}
