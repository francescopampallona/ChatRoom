package com.francescopampallona.chatroom.service;

import com.francescopampallona.chatroom.dto.response.InviteResponse;
import com.francescopampallona.chatroom.enums.InviteStatus;
import com.francescopampallona.chatroom.enums.RoomRole;
import com.francescopampallona.chatroom.enums.RoomType;
import com.francescopampallona.chatroom.mapper.RoomInviteMapper;
import com.francescopampallona.chatroom.model.Room;
import com.francescopampallona.chatroom.model.RoomInvite;
import com.francescopampallona.chatroom.model.RoomMember;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.RoomInviteRepository;
import com.francescopampallona.chatroom.repository.RoomMemberRepository;
import com.francescopampallona.chatroom.repository.RoomRepository;
import com.francescopampallona.chatroom.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InviteService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomInviteRepository roomInviteRepository;
    private final UserRepository userRepository;
    private final RoomInviteMapper roomInviteMapper;

    public InviteService(
            RoomRepository roomRepository,
            RoomMemberRepository roomMemberRepository,
            RoomInviteRepository roomInviteRepository,
            UserRepository userRepository,
            RoomInviteMapper roomInviteMapper
    ) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.roomInviteRepository = roomInviteRepository;
        this.userRepository = userRepository;
        this.roomInviteMapper = roomInviteMapper;
    }

    @Transactional
    @PreAuthorize("@inviteSecurity.canInvite(#roomId, authentication)")
    public InviteResponse inviteUserToPrivateRoom(
            Long roomId,
            String usernameToInvite,
            User currentUser
    ) {
        Room room = getPrivateRoom(roomId);

        User invitedUser = getUserToInvite(usernameToInvite);

        validateInvite(roomId, invitedUser, currentUser);

        RoomInvite invite = buildPendingInvite(room, invitedUser, currentUser);

        RoomInvite savedInvite = roomInviteRepository.save(invite);

        return roomInviteMapper.toDto(savedInvite);
    }

    private Room getPrivateRoom(Long roomId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room non trovata"));

        if (room.getType() != RoomType.PRIVATE) {
            throw new RuntimeException("Puoi invitare utenti solo in room private");
        }

        return room;
    }

    private User getUserToInvite(String usernameToInvite) {

        return userRepository.findByUsername(
                        usernameToInvite.trim().toLowerCase()
                )
                .orElseThrow(() -> new RuntimeException("Utente da invitare non trovato"));
    }

    private void validateInvite(
            Long roomId,
            User invitedUser,
            User currentUser
    ) {
        if (invitedUser.getId().equals(currentUser.getId())) {
            throw new RuntimeException("Non puoi invitare te stesso");
        }

        if (roomMemberRepository.existsByRoomIdAndUserId(roomId, invitedUser.getId())) {
            throw new RuntimeException("L'utente è già membro della room");
        }

        if (roomInviteRepository.existsByRoomIdAndInvitedUserIdAndStatus(
                roomId,
                invitedUser.getId(),
                InviteStatus.PENDING
        )) {
            throw new RuntimeException("L'utente ha già un invito in sospeso");
        }
    }

    private RoomInvite buildPendingInvite(
            Room room,
            User invitedUser,
            User currentUser
    ) {
        return RoomInvite.builder()
                .room(room)
                .invitedUser(invitedUser)
                .invitedBy(currentUser)
                .status(InviteStatus.PENDING)
                .build();
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> getMyPendingInvites(User currentUser) {

        return roomInviteRepository
                .findByInvitedUserIdAndStatus(
                        currentUser.getId(),
                        InviteStatus.PENDING
                )
                .stream()
                .map(roomInviteMapper::toDto)
                .toList();
    }

    @Transactional
    @PreAuthorize("@inviteSecurity.canAccept(#inviteId, authentication)")
    public InviteResponse acceptInvite(Long inviteId, User currentUser) {

        RoomInvite invite = roomInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invito non trovato"));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new RuntimeException("Questo invito è già stato gestito");
        }

        boolean alreadyMember = roomMemberRepository.existsByRoomIdAndUserId(
                invite.getRoom().getId(),
                currentUser.getId()
        );

        if (alreadyMember) {
            throw new RuntimeException("Sei già membro di questa room");
        }

        RoomMember roomMember = RoomMember.builder()
                .room(invite.getRoom())
                .user(currentUser)
                .role(RoomRole.MEMBER)
                .build();

        roomMemberRepository.save(roomMember);

        invite.setStatus(InviteStatus.ACCEPTED);//modifica salvata in automatico dal meccanismo del DirtyChecking

        return roomInviteMapper.toDto(invite);
    }

    @Transactional
    @PreAuthorize("@inviteSecurity.canAccept(#inviteId, authentication)")
    public InviteResponse declineInvite(Long inviteId) {

        RoomInvite invite = roomInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invito non trovato"));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new RuntimeException("Questo invito è già stato gestito");
        }

        invite.setStatus(InviteStatus.DECLINED);

        return roomInviteMapper.toDto(invite);
    }
}