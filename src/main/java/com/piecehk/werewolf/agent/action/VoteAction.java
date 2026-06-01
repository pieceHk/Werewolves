package com.piecehk.werewolf.agent.action;

public record VoteAction(Integer targetSeat) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.VOTE;
    }
}
