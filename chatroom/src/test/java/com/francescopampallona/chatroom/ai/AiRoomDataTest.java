package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.dto.response.MessageResponse;
import com.francescopampallona.chatroom.mapper.MessageMapper;
import com.francescopampallona.chatroom.model.Message;
import com.francescopampallona.chatroom.model.Room;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.MessageRepository;
import com.francescopampallona.chatroom.repository.RoomMemberRepository;
import com.francescopampallona.chatroom.repository.RoomRepository;
import com.francescopampallona.chatroom.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiRoomDataTest {
    private RoomRepository rooms;
    private UserRepository users;
    private RoomMemberRepository members;
    private MessageRepository messages;
    private MessageMapper mapper;
    private ApplicationEventPublisher events;
    private AiRoomData data;
    private Room room;
    private User agent;
    private User human;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        users = mock(UserRepository.class);
        members = mock(RoomMemberRepository.class);
        messages = mock(MessageRepository.class);
        mapper = mock(MessageMapper.class);
        events = mock(ApplicationEventPublisher.class);
        data = new AiRoomData(rooms, users, members, messages, mapper, events);
        room = Room.builder().id(7L).systemKey(AiRoomSeeder.ROOM_KEY).build();
        agent = User.builder().id(10L).username("aria.ai").agentKey("aria").build();
        human = User.builder().id(20L).username("francesco").build();
        when(rooms.findById(7L)).thenReturn(Optional.of(room));
        when(users.findByAgentKey("aria")).thenReturn(Optional.of(agent));
    }

    @Test
    void requiresAuthenticatedHumanMembershipAndAiRoom() {
        var authentication = new UsernamePasswordAuthenticationToken(human, null, Collections.emptyList());
        assertThrows(AccessDeniedException.class, () -> data.requireAccess(7L, null));
        assertThrows(AccessDeniedException.class, () -> data.requireAccess(7L, authentication));
        when(members.existsByRoomIdAndUserId(7L, 20L)).thenReturn(true);
        assertDoesNotThrow(() -> data.requireAccess(7L, authentication));
        when(rooms.findById(8L)).thenReturn(Optional.of(Room.builder().id(8L).build()));
        when(members.existsByRoomIdAndUserId(8L, 20L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> data.requireAccess(8L, authentication));
        assertThrows(AccessDeniedException.class, () -> data.requireAccess(7L,
                new UsernamePasswordAuthenticationToken(agent, null, Collections.emptyList())));
    }

    @Test
    void readsOnlyRequestedRoomInChronologicalOrderWithAgentIdentity() {
        Message newest = Message.builder().sender(human).content("La domanda piu recente").build();
        Message older = Message.builder().sender(agent).content("Un contributo precedente").build();
        when(messages.findByRoomId(eq(7L), any())).thenReturn(new PageImpl<>(List.of(newest, older)));

        AiRoomData.Context context = data.context(7L, AiPersona.ALL.get(0));

        assertEquals(10L, context.agentId());
        assertTrue(context.systemPrompt().contains("Sei Aria"));
        assertTrue(context.systemPrompt().contains("Non fingere"));
        assertEquals("assistant", context.messages().get(0).role());
        assertEquals("[Persona francesco] La domanda piu recente", context.messages().get(1).content());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findByRoomId(eq(7L), page.capture());
        assertEquals(20, page.getValue().getPageSize());
        verifyNoMoreInteractions(messages);
    }

    @Test
    void capsContextCharactersAndKeepsLatestMessage() {
        List<Message> history = new ArrayList<>();
        history.add(Message.builder().sender(human).content("ULTIMO " + "x".repeat(5000)).build());
        for (int i = 1; i < 20; i++) history.add(Message.builder().sender(human).content("y".repeat(5000)).build());
        when(messages.findByRoomId(eq(7L), any())).thenReturn(new PageImpl<>(history));
        var context = data.context(7L, AiPersona.ALL.get(0));
        assertTrue(context.messages().stream().mapToInt(message -> message.content().length()).sum() < 13_000);
        assertTrue(context.messages().get(context.messages().size() - 1).content().contains("ULTIMO"));
    }

    @Test
    void savePublishesAnAgentEventAndRejectsOrdinaryRooms() {
        when(users.findById(10L)).thenReturn(Optional.of(agent));
        when(members.existsByRoomIdAndUserId(7L, 10L)).thenReturn(true);
        when(messages.save(any())).thenAnswer(call -> call.getArgument(0));
        when(mapper.toDto(any())).thenReturn(MessageResponse.builder().roomId(7L).senderIsAgent(true).build());
        data.saveReply(7L, 10L, " Una risposta locale ");
        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messages).save(saved.capture());
        assertEquals("Una risposta locale", saved.getValue().getContent());
        ArgumentCaptor<MessageCommittedEvent> event = ArgumentCaptor.forClass(MessageCommittedEvent.class);
        verify(events).publishEvent(event.capture());
        assertTrue(event.getValue().senderAgent());
        assertTrue(event.getValue().aiRoom());
        assertThrows(IllegalStateException.class, () -> data.saveReply(7L, 10L, " "));
        when(rooms.findById(8L)).thenReturn(Optional.of(Room.builder().id(8L).build()));
        assertThrows(IllegalArgumentException.class, () -> data.saveReply(8L, 10L, "ciao"));
    }
}
