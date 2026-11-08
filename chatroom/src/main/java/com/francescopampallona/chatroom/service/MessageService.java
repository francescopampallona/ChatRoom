package com.francescopampallona.chatroom.service;

import com.francescopampallona.chatroom.dto.request.MessageRequest;
import com.francescopampallona.chatroom.dto.response.MessageResponse;
import com.francescopampallona.chatroom.dto.response.PageResponse;
import com.francescopampallona.chatroom.mapper.MessageMapper;
import com.francescopampallona.chatroom.model.Message;
import com.francescopampallona.chatroom.model.Room;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.MessageRepository;
import com.francescopampallona.chatroom.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final MessageMapper messageMapper;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int MAX_PAGE_SIZE = 100;

    @Transactional(readOnly = true)
    @PreAuthorize("@roomSecurity.isMember(#roomId, authentication)")
    public PageResponse<MessageResponse> getMessageHistory(
            Long roomId,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "sentAt")
        );

        Page<Message> result =
                messageRepository.findByRoomId(roomId, pageable);

        return PageResponse.<MessageResponse>builder()
                .content(
                        result.getContent()
                                .stream()
                                .map(messageMapper::toDto)
                                .toList()
                )
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional
    @PreAuthorize("@roomSecurity.isMember(#roomId, authentication)")
    public MessageResponse sendMessage(
            Long roomId,
            MessageRequest request,
            Authentication authentication
    ) {
        User currentUser = (User)authentication.getPrincipal();
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room non trovata"));

        Message message = Message.builder()
                .room(room)
                .sender(currentUser)
                .content(request.getContent().trim())
                .build();

        Message savedMessage = messageRepository.save(message);

        MessageResponse response = messageMapper.toDto(savedMessage);

        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId,
                response
        );

        return response;
    }
}