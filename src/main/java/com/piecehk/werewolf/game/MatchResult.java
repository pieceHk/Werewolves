package com.piecehk.werewolf.game;

import com.piecehk.werewolf.agent.AgentJournal;
import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.infra.output.MatchWorkspace;

public record MatchResult(Game game, AgentJournal journal, MatchWorkspace workspace, Camp winner) {
}
