package com.piecehk.werewolf.agent.llm;

public final class LLMException extends RuntimeException {
    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
