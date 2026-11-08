package com.francescopampallona.chatroom.security;

import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("roomSecurity")
@RequiredArgsConstructor
public class RoomSecurity {

    private final RoomMemberRepository roomMemberRepository;

    public boolean isMember(Long roomId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();

        return roomMemberRepository.existsByRoomIdAndUserId(
                roomId,
                user.getId()
        );
    }
}