package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.dto.response.MessageResponse;

public record MessageCommittedEvent(MessageResponse message, boolean aiRoom, boolean senderAgent) { }
