package com.francescopampallona.chatroom.enums;

public enum RoomRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean canInvite() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canDeleteRoom() {
        return this == OWNER;
    }
}