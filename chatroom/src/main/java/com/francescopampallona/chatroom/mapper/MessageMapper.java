package com.francescopampallona.chatroom.mapper;

import com.francescopampallona.chatroom.dto.response.MessageResponse;
import com.francescopampallona.chatroom.model.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    @Mapping(source = "room.id", target = "roomId")
    @Mapping(source = "sender.id", target = "senderId")
    @Mapping(source = "sender.username", target = "senderUsername")
    MessageResponse toDto(Message message);
}
