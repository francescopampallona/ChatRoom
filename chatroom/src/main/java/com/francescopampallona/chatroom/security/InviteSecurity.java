package com.francescopampallona.chatroom.security;

import com.francescopampallona.chatroom.enums.RoomRole;
import com.francescopampallona.chatroom.model.RoomMember;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.RoomInviteRepository;
import com.francescopampallona.chatroom.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("inviteSecurity")
@RequiredArgsConstructor
public class InviteSecurity {
    private final RoomMemberRepository roomMemberRepository;
    private final RoomInviteRepository roomInviteRepository;

    public boolean canInvite(Long roomId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User currentUser)) {
            return false;
        }

        return roomMemberRepository
                    .findByRoomIdAndUserId(roomId, currentUser.getId())
                    .map(RoomMember::getRole)
                    .map(role -> role == RoomRole.OWNER || role == RoomRole.ADMIN)
                    .orElse(false);
        }
    public boolean canAccept(Long inviteId, Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof User currentUser)) {
            return false;
        }

        return roomInviteRepository.findById(inviteId)
                .map(invite -> invite.getInvitedUser().getId().equals(currentUser.getId()))
                .orElse(false);
    }
}
