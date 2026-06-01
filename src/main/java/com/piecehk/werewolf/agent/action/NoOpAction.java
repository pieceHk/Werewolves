package com.piecehk.werewolf.agent.action;

public record NoOpAction() implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.NOOP;
    }
}
