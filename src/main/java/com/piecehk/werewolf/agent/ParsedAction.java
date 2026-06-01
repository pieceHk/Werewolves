package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.agent.action.AgentAction;

public record ParsedAction(String reasoning, AgentAction action, String warning) {
}
