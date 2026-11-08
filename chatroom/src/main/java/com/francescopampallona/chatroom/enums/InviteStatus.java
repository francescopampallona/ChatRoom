package com.francescopampallona.chatroom.enums;

public enum InviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED;

    public boolean isFinal() {
        return this == ACCEPTED || this == DECLINED;
    }
}