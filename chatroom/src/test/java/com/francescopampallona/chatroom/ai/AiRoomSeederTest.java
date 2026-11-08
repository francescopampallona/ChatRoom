package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.enums.RoomType;
import com.francescopampallona.chatroom.model.Room;
import com.francescopampallona.chatroom.model.RoomMember;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.RoomMemberRepository;
import com.francescopampallona.chatroom.repository.RoomRepository;
import com.francescopampallona.chatroom.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiRoomSeederTest {
    @Test
    void seedIsIdempotentAndNeverRepurposesAnExistingHumanUsername() {
        UserRepository users = mock(UserRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        RoomMemberRepository members = mock(RoomMemberRepository.class);
        Map<String, User> agents = new HashMap<>();
        AtomicLong ids = new AtomicLong(100);
        AtomicReference<Room> savedRoom = new AtomicReference<>();
        Set<Long> memberships = new HashSet<>();
        when(users.findByAgentKey(anyString())).thenAnswer(call -> Optional.ofNullable(agents.get(call.getArgument(0))));
        when(users.existsByUsername("aria.ai")).thenReturn(true);
        when(users.save(any(User.class))).thenAnswer(call -> {
            User agent = call.getArgument(0);
            agent.setId(ids.getAndIncrement());
            agents.put(agent.getAgentKey(), agent);
            return agent;
        });
        when(rooms.findBySystemKey(AiRoomSeeder.ROOM_KEY)).thenAnswer(call -> Optional.ofNullable(savedRoom.get()));
        when(rooms.save(any(Room.class))).thenAnswer(call -> {
            Room room = call.getArgument(0);
            room.setId(7L);
            savedRoom.set(room);
            return room;
        });
        when(members.existsByRoomIdAndUserId(eq(7L), anyLong()))
                .thenAnswer(call -> memberships.contains(call.getArgument(1)));
        when(members.save(any(RoomMember.class))).thenAnswer(call -> {
            RoomMember member = call.getArgument(0);
            memberships.add(member.getUser().getId());
            return member;
        });
        AiRoomSeeder seeder = new AiRoomSeeder(new AiProperties(), users, rooms, members);

        seeder.run(null);
        seeder.run(null);

        assertEquals(3, agents.size());
        assertEquals("aria.ai.1", agents.get("aria").getUsername());
        assertTrue(agents.values().stream().allMatch(User::isAgent));
        assertTrue(agents.values().stream().allMatch(user -> user.getPasswordHash().startsWith("!disabled-ai-account-")));
        assertTrue(savedRoom.get().isAiRoom());
        assertEquals(RoomType.PUBLIC, savedRoom.get().getType());
        assertEquals("AI Lounge", savedRoom.get().getName());
        verify(users, times(3)).save(any(User.class));
        verify(users, never()).findByUsername(anyString());
        verify(rooms, times(1)).save(any(Room.class));
        verify(members, times(3)).save(any(RoomMember.class));
    }

    @Test
    void disabledFeatureDoesNotSeedAnything() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(false);
        UserRepository users = mock(UserRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        RoomMemberRepository members = mock(RoomMemberRepository.class);
        new AiRoomSeeder(properties, users, rooms, members).run(null);
        verifyNoInteractions(users, rooms, members);
    }

    @Test
    void existingRowsWithNullKeysRemainHumanAndOrdinaryRooms() {
        assertFalse(User.builder().id(1L).username("aria.ai").build().isAgent());
        assertFalse(Room.builder().id(1L).name("AI Lounge").build().isAiRoom());
    }
}
