package com.piecehk.werewolf.agent.action;

public record WolfKillAction(Integer targetSeat) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.WOLF_KILL;
    }
}
