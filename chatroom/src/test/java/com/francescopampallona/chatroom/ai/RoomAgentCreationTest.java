package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.dto.request.CreateRoomRequest;
import com.francescopampallona.chatroom.dto.response.RoomDto;
import com.francescopampallona.chatroom.enums.RoomRole;
import com.francescopampallona.chatroom.enums.RoomType;
import com.francescopampallona.chatroom.mapper.RoomMapper;
import com.francescopampallona.chatroom.model.Room;
import com.francescopampallona.chatroom.model.RoomMember;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.RoomMemberRepository;
import com.francescopampallona.chatroom.repository.RoomRepository;
import com.francescopampallona.chatroom.repository.UserRepository;
import com.francescopampallona.chatroom.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomAgentCreationTest {
    private RoomMapper mapper;
    private RoomRepository rooms;
    private RoomMemberRepository members;
    private AiRoomData agents;
    private RoomService service;
    private User owner;

    @BeforeEach
    void setUp() {
        mapper = mock(RoomMapper.class);
        rooms = mock(RoomRepository.class);
        members = mock(RoomMemberRepository.class);
        agents = mock(AiRoomData.class);
        service = new RoomService(mapper, mock(UserRepository.class), rooms, members, agents);
        owner = User.builder().id(9L).username("owner").build();
    }

    @Test
    void invalidSelectionDoesNotCreateRoomOrOwnerMembership() {
        CreateRoomRequest request = request(List.of("unknown"));
        doThrow(new IllegalArgumentException("Agente AI sconosciuto"))
                .when(agents).validateAgentKeys(request.getAgentKeys());

        assertThrows(IllegalArgumentException.class, () -> service.createRoom(request, owner));
        verifyNoInteractions(mapper, rooms, members);
        verify(agents, never()).configureAgents(any(), any(), any());
    }

    @Test
    void addsRequestedAgentsOnlyAfterCreatingOwnerMembership() {
        CreateRoomRequest request = request(List.of("aria", "mira"));
        Room room = Room.builder().name(request.getName()).description(request.getDescription())
                .type(request.getType()).build();
        when(mapper.toEntity(request)).thenReturn(room);
        when(rooms.save(room)).thenAnswer(call -> {
            room.setId(31L);
            return room;
        });
        RoomDto response = RoomDto.builder().id(31L).ownerId(9L).build();
        when(mapper.toDto(room)).thenReturn(response);

        assertSame(response, service.createRoom(request, owner));

        assertSame(owner, room.getOwner());
        ArgumentCaptor<RoomMember> membership = ArgumentCaptor.forClass(RoomMember.class);
        InOrder order = inOrder(agents, rooms, members, mapper);
        order.verify(agents).validateAgentKeys(List.of("aria", "mira"));
        order.verify(mapper).toEntity(request);
        order.verify(rooms).save(room);
        order.verify(members).save(membership.capture());
        order.verify(agents).configureAgents(31L, owner, List.of("aria", "mira"));
        order.verify(mapper).toDto(room);
        assertEquals(RoomRole.OWNER, membership.getValue().getRole());
        assertSame(owner, membership.getValue().getUser());
        assertSame(room, membership.getValue().getRoom());
    }

    @Test
    void legacyRequestWithoutAgentsStillCreatesAnOrdinaryRoom() {
        CreateRoomRequest request = request(null);
        Room room = Room.builder().id(31L).name(request.getName()).type(RoomType.PUBLIC).build();
        when(mapper.toEntity(request)).thenReturn(room);
        when(rooms.save(room)).thenReturn(room);

        service.createRoom(request, owner);

        verify(agents).validateAgentKeys(null);
        verify(agents).configureAgents(31L, owner, null);
        verify(members).save(any(RoomMember.class));
        assertFalse(room.isAiRoom());
    }

    @Test
    void configurationFailurePropagatesOutOfTransactionalCreation() {
        CreateRoomRequest request = request(List.of("aria"));
        Room room = Room.builder().id(31L).build();
        when(mapper.toEntity(request)).thenReturn(room);
        when(rooms.save(room)).thenReturn(room);
        doThrow(new IllegalStateException("Agente non disponibile"))
                .when(agents).configureAgents(31L, owner, List.of("aria"));

        assertThrows(IllegalStateException.class, () -> service.createRoom(request, owner));
        verify(mapper, never()).toDto(any(Room.class));
    }

    private CreateRoomRequest request(List<String> keys) {
        return CreateRoomRequest.builder().name("Missioni spaziali")
                .description("Robot o astronauti per esplorare Marte?")
                .type(RoomType.PRIVATE).agentKeys(keys).build();
    }
}
