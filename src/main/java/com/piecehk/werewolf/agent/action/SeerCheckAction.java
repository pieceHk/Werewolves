package com.piecehk.werewolf.agent.action;

public record SeerCheckAction(Integer targetSeat) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.SEER_CHECK;
    }
}
