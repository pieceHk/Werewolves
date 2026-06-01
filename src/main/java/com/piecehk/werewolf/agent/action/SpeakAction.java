package com.piecehk.werewolf.agent.action;

public record SpeakAction(String speech, boolean withdraw) implements AgentAction {
    public SpeakAction(String speech) {
        this(speech, false);
    }

    @Override
    public ActionType type() {
        return ActionType.SPEAK;
    }
}
