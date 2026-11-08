package com.francescopampallona.chatroom.mapper;

import com.francescopampallona.chatroom.dto.response.InviteResponse;
import com.francescopampallona.chatroom.model.RoomInvite;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoomInviteMapper {
    @Mapping(source = "room.id", target = "roomId")
    @Mapping(source = "room.name", target = "roomName")
    @Mapping(source = "invitedUser.id", target = "invitedUserId")
    @Mapping(source = "invitedUser.username", target = "invitedUsername")
    @Mapping(source = "invitedBy.id", target = "invitedByUserId")
    @Mapping(source = "invitedBy.username", target = "invitedByUsername")
    InviteResponse toDto(RoomInvite invite);
}
