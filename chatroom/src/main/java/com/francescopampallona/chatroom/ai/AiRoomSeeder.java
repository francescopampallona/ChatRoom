package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.enums.RoomRole;
import com.francescopampallona.chatroom.enums.RoomType;
import com.francescopampallona.chatroom.model.Room;
import com.francescopampallona.chatroom.model.RoomMember;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.RoomMemberRepository;
import com.francescopampallona.chatroom.repository.RoomRepository;
import com.francescopampallona.chatroom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiRoomSeeder implements ApplicationRunner {
    public static final String ROOM_KEY = "ai-lounge";
    private final AiProperties properties;
    private final UserRepository users;
    private final RoomRepository rooms;
    private final RoomMemberRepository members;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        List<User> agents = AiPersona.ALL.stream().map(this::findOrCreateAgent).toList();
        Room room = rooms.findBySystemKey(ROOM_KEY).orElseGet(() -> rooms.save(Room.builder()
                .systemKey(ROOM_KEY)
                .name("AI Lounge")
                .description("Una stanza pubblica con Aria, Bruno e Mira, tre agenti AI locali. Entra e partecipa alla conversazione.")
                .type(RoomType.PUBLIC)
                .owner(agents.get(0))
                .build()));
        for (User agent : agents) {
            if (!members.existsByRoomIdAndUserId(room.getId(), agent.getId())) {
                members.save(RoomMember.builder().room(room).user(agent)
                        .role(agent.getId().equals(room.getOwner().getId()) ? RoomRole.OWNER : RoomRole.MEMBER)
                        .build());
            }
        }
    }

    private User findOrCreateAgent(AiPersona persona) {
        return users.findByAgentKey(persona.key()).orElseGet(() -> {
            String baseUsername = persona.key() + ".ai";
            String username = baseUsername;
            int suffix = 1;
            // Never turn an existing human account into an agent, even when names collide.
            while (users.existsByUsername(username)) {
                username = baseUsername + "." + suffix++;
            }
            String email = UUID.randomUUID() + "@agents.invalid";
            while (users.existsByEmail(email)) {
                email = UUID.randomUUID() + "@agents.invalid";
            }
            return users.save(User.builder().username(username).email(email)
                    .passwordHash("!disabled-ai-account-" + UUID.randomUUID())
                    .agentKey(persona.key()).build());
        });
    }
}
