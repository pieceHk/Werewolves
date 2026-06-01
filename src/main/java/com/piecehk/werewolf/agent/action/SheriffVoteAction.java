package com.piecehk.werewolf.agent.action;

public record SheriffVoteAction(Integer targetSeat) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.SHERIFF_VOTE;
    }
}
