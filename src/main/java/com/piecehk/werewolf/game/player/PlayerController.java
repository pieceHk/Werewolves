package com.piecehk.werewolf.game.player;

import com.piecehk.werewolf.agent.AgentContext;
import com.piecehk.werewolf.agent.ParsedAction;
import com.piecehk.werewolf.agent.action.ActionType;

import java.util.Set;

public interface PlayerController {
    ParsedAction decide(AgentContext context, ActionType required, Set<Integer> validTargets, int maxSpeechChars);

    boolean isHuman();
}
