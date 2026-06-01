package com.piecehk.werewolf.agent.action;

public record HunterShootAction(Integer targetSeat) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.HUNTER_SHOOT;
    }
}
