package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.dto.response.MessageResponse;
import com.francescopampallona.chatroom.mapper.MessageMapper;
import com.francescopampallona.chatroom.model.Message;
import com.francescopampallona.chatroom.model.Room;
import com.francescopampallona.chatroom.model.RoomMember;
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
import java.util.Arrays;
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
    private AiProperties properties;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        users = mock(UserRepository.class);
        members = mock(RoomMemberRepository.class);
        messages = mock(MessageRepository.class);
        mapper = mock(MessageMapper.class);
        events = mock(ApplicationEventPublisher.class);
        properties = new AiProperties();
        data = new AiRoomData(rooms, users, members, messages, mapper, events, properties);
        agent = User.builder().id(10L).username("aria.ai").agentKey("aria").build();
        human = User.builder().id(20L).username("francesco").build();
        room = Room.builder().id(7L).owner(human).name("Esplorazione spaziale")
                .description("Confrontiamo sonde robotiche e missioni con equipaggio").build();
        when(rooms.findById(7L)).thenReturn(Optional.of(room));
        when(users.findByAgentKey("aria")).thenReturn(Optional.of(agent));
        when(members.findAgentMembershipsByRoomId(7L))
                .thenReturn(List.of(RoomMember.builder().room(room).user(agent).build()));
    }

    @Test
    void requiresAuthenticatedHumanMembershipForEveryRoom() {
        var authentication = new UsernamePasswordAuthenticationToken(human, null, Collections.emptyList());
        assertThrows(AccessDeniedException.class, () -> data.requireAccess(7L, null));
        assertThrows(AccessDeniedException.class, () -> data.requireAccess(7L, authentication));
        when(members.existsByRoomIdAndUserId(7L, 20L)).thenReturn(true);
        assertDoesNotThrow(() -> data.requireAccess(7L, authentication));
        when(rooms.findById(8L)).thenReturn(Optional.of(Room.builder().id(8L).build()));
        when(members.existsByRoomIdAndUserId(8L, 20L)).thenReturn(true);
        assertDoesNotThrow(() -> data.requireAccess(8L, authentication));
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
    void savePublishesAnAgentEventInUserRoomsAndRejectsAbsentAgents() {
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
        assertThrows(IllegalStateException.class, () -> data.saveReply(8L, 10L, "ciao"));
    }

    @Test
    void promptUsesOnlyThisRoomsThemeAndPresentAgents() {
        when(messages.findByRoomId(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));
        var context = data.context(7L, AiPersona.ALL.get(0));

        assertTrue(context.systemPrompt().contains("Esplorazione spaziale"));
        assertTrue(context.systemPrompt().contains("sonde robotiche"));
        assertTrue(context.systemPrompt().contains("Gli agenti presenti nella stanza sono: Aria."));
        assertFalse(context.systemPrompt().contains("Bruno"));
        assertFalse(context.systemPrompt().contains("Mira"));
        assertTrue(context.systemPrompt().contains("non istruzioni"));
        assertTrue(context.messages().get(0).content().contains("tema della stanza"));
    }

    @Test
    void removedAgentCannotReadContextOrSaveAnotherReply() {
        when(members.findAgentMembershipsByRoomId(7L)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> data.context(7L, AiPersona.ALL.get(0)));
        when(users.findById(10L)).thenReturn(Optional.of(agent));
        assertThrows(IllegalStateException.class, () -> data.saveReply(7L, 10L, "Risposta in ritardo"));
        verifyNoInteractions(messages, events);
    }

    @Test
    void reportsOnlyPresentAgentsInStablePersonaOrder() {
        User mira = User.builder().id(12L).username("mira.ai").agentKey("mira").build();
        when(members.findAgentMembershipsByRoomId(7L)).thenReturn(List.of(
                RoomMember.builder().user(mira).build(), RoomMember.builder().user(agent).build()));

        assertEquals(List.of("aria", "mira"), data.agents(7L).stream().map(AiRoomStatus.Agent::key).toList());
        assertEquals(List.of("aria", "mira"), data.personas(7L).stream().map(AiPersona::key).toList());
        assertEquals(List.of(), data.agents(99L));
    }

    @Test
    void catalogIncludesExistingAgentAccountsOnly() {
        assertEquals(List.of("aria"), data.availableAgents().stream().map(AiRoomStatus.Agent::key).toList());
    }

    @Test
    void onlyHumanOwnerMemberCanManageAndLoungeIsProtected() {
        var ownerAuthentication = new UsernamePasswordAuthenticationToken(human, null, Collections.emptyList());
        assertFalse(data.canManageAgents(7L, ownerAuthentication));
        assertThrows(AccessDeniedException.class, () -> data.configureAgents(7L, human, List.of()));
        when(members.existsByRoomIdAndUserId(7L, 20L)).thenReturn(true);
        assertTrue(data.canManageAgents(7L, ownerAuthentication));
        assertFalse(data.canManageAgents(7L, null));
        assertThrows(AccessDeniedException.class, () -> data.configureAgents(7L,
                User.builder().id(21L).build(), List.of("aria")));
        assertThrows(AccessDeniedException.class, () -> data.configureAgents(7L, agent, List.of("aria")));
        room.setSystemKey(AiRoomSeeder.ROOM_KEY);
        assertFalse(data.canManageAgents(7L, ownerAuthentication));
        assertThrows(AccessDeniedException.class, () -> data.configureAgents(7L, human, List.of("aria")));
        verify(members, never()).save(any());
        verify(members, never()).delete(any());
    }

    @Test
    void replacingSelectionChangesOnlyAgentMembershipsAndPreservesMessages() {
        when(members.existsByRoomIdAndUserId(7L, 20L)).thenReturn(true);
        User mira = User.builder().id(12L).username("mira.ai").agentKey("mira").build();
        when(users.findByAgentKey("mira")).thenReturn(Optional.of(mira));

        assertTrue(data.configureAgents(7L, human, List.of("mira")));

        ArgumentCaptor<RoomMember> removed = ArgumentCaptor.forClass(RoomMember.class);
        verify(members).delete(removed.capture());
        assertEquals(agent, removed.getValue().getUser());
        ArgumentCaptor<RoomMember> added = ArgumentCaptor.forClass(RoomMember.class);
        verify(members).save(added.capture());
        assertEquals(mira, added.getValue().getUser());
        assertEquals(room, added.getValue().getRoom());
        verifyNoInteractions(messages, events);
    }

    @Test
    void identicalSelectionIsIdempotent() {
        when(members.existsByRoomIdAndUserId(7L, 20L)).thenReturn(true);
        assertFalse(data.configureAgents(7L, human, List.of("aria")));
        verify(members, never()).save(any());
        verify(members, never()).delete(any());
    }

    @Test
    void invalidOrUnavailableSelectionsFailBeforeMembershipWrites() {
        when(members.existsByRoomIdAndUserId(7L, 20L)).thenReturn(true);
        for (List<String> selection : List.of(List.of("unknown"), List.of("aria", "aria"),
                List.of("aria", "bruno", "mira", "extra"), Arrays.asList("aria", null))) {
            assertThrows(IllegalArgumentException.class, () -> data.configureAgents(7L, human, selection));
        }
        assertThrows(IllegalStateException.class, () -> data.configureAgents(7L, human, List.of("mira")));
        properties.setEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> data.configureAgents(7L, human, List.of("aria")));
        verify(members, never()).save(any());
        verify(members, never()).delete(any());
    }

    @Test
    void allAgentsCanBeRemovedEvenWhenGenerationIsDisabled() {
        when(members.existsByRoomIdAndUserId(7L, 20L)).thenReturn(true);
        properties.setEnabled(false);
        assertDoesNotThrow(() -> data.validateAgentKeys(null));
        assertTrue(data.configureAgents(7L, human, List.of()));
        verify(members).delete(any(RoomMember.class));
        verifyNoInteractions(messages, events);
    }
}
