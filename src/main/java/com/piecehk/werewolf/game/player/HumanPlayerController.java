package com.piecehk.werewolf.game.player;

import com.piecehk.werewolf.agent.AgentContext;
import com.piecehk.werewolf.agent.ParsedAction;
import com.piecehk.werewolf.agent.action.ActionType;

import java.util.Set;

public final class HumanPlayerController implements PlayerController {
    @Override
    public ParsedAction decide(AgentContext context, ActionType required, Set<Integer> validTargets, int maxSpeechChars) {
        throw new UnsupportedOperationException("human participation is reserved for v2");
    }

    @Override
    public boolean isHuman() {
        return true;
    }
}
