package com.francescopampallona.chatroom.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageCommitListener {
    private final SimpMessagingTemplate messaging;
    private final AiCoordinator coordinator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(MessageCommittedEvent event) {
        try {
            messaging.convertAndSend("/topic/rooms/" + event.message().getRoomId(), event.message());
        } catch (RuntimeException exception) {
            // The message is already committed. A broker failure must not retry the database write.
            log.warn("Message {} was committed but could not be broadcast", event.message().getId(), exception);
        }
        if (event.aiRoom() && !event.senderAgent()) {
            coordinator.onHumanMessage(event.message().getRoomId());
        }
    }
}
