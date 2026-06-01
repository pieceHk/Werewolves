package com.piecehk.werewolf.agent.action;

public record SheriffRunAction(boolean run) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.SHERIFF_RUN;
    }
}
