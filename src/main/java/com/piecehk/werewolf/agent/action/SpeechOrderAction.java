package com.piecehk.werewolf.agent.action;

import com.piecehk.werewolf.core.model.SpeechOrder;

public record SpeechOrderAction(SpeechOrder order) implements AgentAction {
    @Override
    public ActionType type() {
        return ActionType.SPEECH_ORDER;
    }
}
