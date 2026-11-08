package com.francescopampallona.chatroom.service;

import com.francescopampallona.chatroom.dto.request.CreateRoomRequest;
import com.francescopampallona.chatroom.dto.response.RoomDto;
import com.francescopampallona.chatroom.dto.response.UserDto;
import com.francescopampallona.chatroom.enums.RoomRole;
import com.francescopampallona.chatroom.enums.RoomType;
import com.francescopampallona.chatroom.mapper.RoomMapper;
import com.francescopampallona.chatroom.model.Room;
import com.francescopampallona.chatroom.model.RoomMember;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.RoomMemberRepository;
import com.francescopampallona.chatroom.repository.RoomRepository;
import com.francescopampallona.chatroom.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService {
    private final RoomMapper roomMapper;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    public RoomService(RoomMapper roomMapper, UserRepository userRepository, RoomRepository roomRepository, RoomMemberRepository roomMemberRepository){
        this.roomMapper = roomMapper;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
    }

    @Transactional
    public RoomDto createRoom(CreateRoomRequest request, User owner){

        Room room = roomMapper.toEntity(request);
        room.setOwner(owner);

        Room savedRoom = roomRepository.save(room);

        RoomMember roomMember = RoomMember.builder()
                .room(savedRoom)
                .user(owner)
                .role(RoomRole.OWNER)
                .build();

        roomMemberRepository.save(roomMember);

        return roomMapper.toDto(savedRoom);

    }

    @Transactional(readOnly = true)
    public List<RoomDto> getAllRooms(User user) {

        List<Room> rooms = roomRepository.findRoomsByUser(user);

        return rooms.stream()
                .map(roomMapper::toDto)
                .toList();
    }

    @PreAuthorize("@roomSecurity.isMember(#roomId, authentication)")
    @Transactional(readOnly = true)
    public RoomDto getRoomById(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room non trovata"));

        return roomMapper.toDto(room);
    }

    @Transactional(readOnly = true)
    public List<RoomDto> getAllPublicRooms() {

        return roomRepository.findByType(RoomType.PUBLIC)
                .stream()
                .map(roomMapper::toDto)
                .toList();
    }

    @Transactional
    public RoomDto joinPublicRoom(Long roomId, User user) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room non trovata"));

        if (room.getType() != RoomType.PUBLIC) {
            throw new RuntimeException("Non puoi entrare direttamente in una room privata");
        }

        boolean alreadyMember = roomMemberRepository.existsByRoomIdAndUserId(
                roomId,
                user.getId()
        );

        if (alreadyMember) {
            throw new RuntimeException("Sei già membro di questa room");
        }

        RoomMember roomMember = RoomMember.builder()
                .room(room)
                .user(user)
                .role(RoomRole.MEMBER)
                .build();

        roomMemberRepository.save(roomMember);

        return roomMapper.toDto(room);
    }

    @Transactional
    public void leaveRoom(Long roomId, User user) {

        RoomMember membership = roomMemberRepository
                .findByRoomIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new RuntimeException("Non sei membro di questa room"));

        if (membership.getRole() == RoomRole.OWNER) {
            throw new RuntimeException("Il proprietario non può uscire dalla room");
        }

        roomMemberRepository.delete(membership);
    }
}
