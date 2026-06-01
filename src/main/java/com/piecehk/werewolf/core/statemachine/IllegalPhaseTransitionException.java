package com.piecehk.werewolf.core.statemachine;

public final class IllegalPhaseTransitionException extends RuntimeException {
    public IllegalPhaseTransitionException(String message) {
        super(message);
    }
}
