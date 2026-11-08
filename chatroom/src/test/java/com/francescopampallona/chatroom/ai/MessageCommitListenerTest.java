package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.dto.response.MessageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises Spring's real transactional event dispatch without a database or application server. */
class MessageCommitListenerTest {
    private AnnotationConfigApplicationContext context;
    private SimpMessagingTemplate messaging;
    private AiCoordinator coordinator;
    private LocalTransactionManager transactionManager;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(ListenerConfiguration.class);
        messaging = context.getBean(SimpMessagingTemplate.class);
        coordinator = context.getBean(AiCoordinator.class);
        transactionManager = context.getBean(LocalTransactionManager.class);
        transaction = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void humanAiMessageIsBroadcastAndTriggersTheCoordinatorOnlyAfterCommit() {
        MessageResponse message = message(42L, false);
        doAnswer(invocation -> {
            assertTrue(transactionManager.committed);
            return null;
        }).when(messaging).convertAndSend("/topic/rooms/42", message);
        doAnswer(invocation -> {
            assertTrue(transactionManager.committed);
            return null;
        }).when(coordinator).onHumanMessage(42L);

        transaction.executeWithoutResult(status -> {
            context.publishEvent(new MessageCommittedEvent(message, true, false));
            assertFalse(transactionManager.committed);
            verifyNoInteractions(messaging, coordinator);
        });

        verify(messaging).convertAndSend("/topic/rooms/42", message);
        verify(coordinator).onHumanMessage(42L);
    }

    @Test
    void rolledBackMessageIsNeitherBroadcastNorUsedAsAnAiTrigger() {
        transaction.executeWithoutResult(status -> {
            context.publishEvent(new MessageCommittedEvent(message(42L, false), true, false));
            status.setRollbackOnly();
        });

        assertTrue(transactionManager.rolledBack);
        assertFalse(transactionManager.committed);
        verifyNoInteractions(messaging, coordinator);
    }

    @Test
    void publishingOutsideATransactionDoesNotBypassCommitRequirement() {
        context.publishEvent(new MessageCommittedEvent(message(42L, false), true, false));

        verifyNoInteractions(messaging, coordinator);
    }

    @Test
    void agentMessagesAreBroadcastAfterCommitWithoutRestartingAutonomousBudget() {
        MessageResponse message = message(42L, true);
        transaction.executeWithoutResult(status -> {
            context.publishEvent(new MessageCommittedEvent(message, true, true));
            verifyNoInteractions(messaging, coordinator);
        });

        verify(messaging).convertAndSend("/topic/rooms/42", message);
        verifyNoInteractions(coordinator);
    }

    @Test
    void humanMessagesInOrdinaryRoomsAreBroadcastWithoutTriggeringAi() {
        MessageResponse message = message(87L, false);
        transaction.executeWithoutResult(status ->
                context.publishEvent(new MessageCommittedEvent(message, false, false)));

        verify(messaging).convertAndSend("/topic/rooms/87", message);
        verifyNoInteractions(coordinator);
    }

    @Test
    void brokerFailureAfterCommitStillTriggersHumanResponseWithoutRetryingBroadcast() {
        MessageResponse message = message(42L, false);
        doThrow(new IllegalStateException("Broker unavailable"))
                .when(messaging).convertAndSend("/topic/rooms/42", message);

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status ->
                context.publishEvent(new MessageCommittedEvent(message, true, false))));

        assertTrue(transactionManager.committed);
        assertFalse(transactionManager.rolledBack);
        verify(messaging, times(1)).convertAndSend("/topic/rooms/42", message);
        verify(coordinator).onHumanMessage(42L);
        verifyNoMoreInteractions(messaging, coordinator);
    }

    @Test
    void brokerFailureForCommittedAgentReplyDoesNotTriggerAnotherGeneration() {
        MessageResponse message = message(42L, true);
        doThrow(new IllegalStateException("Broker unavailable"))
                .when(messaging).convertAndSend("/topic/rooms/42", message);

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status ->
                context.publishEvent(new MessageCommittedEvent(message, true, true))));

        assertTrue(transactionManager.committed);
        verify(messaging, times(1)).convertAndSend("/topic/rooms/42", message);
        verifyNoInteractions(coordinator);
    }

    private MessageResponse message(Long roomId, boolean agent) {
        return MessageResponse.builder()
                .id(7L)
                .roomId(roomId)
                .senderId(9L)
                .senderIsAgent(agent)
                .content("Messaggio")
                .build();
    }

    @Configuration
    @EnableTransactionManagement
    static class ListenerConfiguration {
        @Bean
        SimpMessagingTemplate messaging() {
            return mock(SimpMessagingTemplate.class);
        }

        @Bean
        AiCoordinator coordinator() {
            return mock(AiCoordinator.class);
        }

        @Bean
        MessageCommitListener listener(SimpMessagingTemplate messaging, AiCoordinator coordinator) {
            return new MessageCommitListener(messaging, coordinator);
        }

        @Bean
        LocalTransactionManager transactionManager() {
            return new LocalTransactionManager();
        }
    }

    static class LocalTransactionManager extends AbstractPlatformTransactionManager {
        boolean committed;
        boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            committed = false;
            rolledBack = false;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
