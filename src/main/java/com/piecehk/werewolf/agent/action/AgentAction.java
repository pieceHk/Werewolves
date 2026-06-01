package com.piecehk.werewolf.agent.action;

public sealed interface AgentAction permits SpeakAction, VoteAction, WolfKillAction, SeerCheckAction,
        WitchAction, HunterShootAction, NoOpAction {
    ActionType type();
}
