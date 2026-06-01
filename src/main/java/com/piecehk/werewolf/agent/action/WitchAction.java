package com.piecehk.werewolf.agent.action;

public record WitchAction(boolean useAntidote, Integer poisonSeat) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.WITCH;
    }
}
