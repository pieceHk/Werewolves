package com.piecehk.werewolf.agent.action;

public record BadgeTransferAction(Integer targetSeat) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.BADGE_TRANSFER;
    }
}
