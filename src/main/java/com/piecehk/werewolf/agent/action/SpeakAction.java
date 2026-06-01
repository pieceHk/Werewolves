package com.piecehk.werewolf.agent.action;

public record SpeakAction(String speech) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.SPEAK;
    }
}
