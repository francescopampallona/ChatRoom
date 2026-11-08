package com.francescopampallona.chatroom.ai;

public class AiBusyException extends IllegalStateException {
    public AiBusyException() {
        super("Un agente sta gia rispondendo. Riprova tra poco.");
    }
}
