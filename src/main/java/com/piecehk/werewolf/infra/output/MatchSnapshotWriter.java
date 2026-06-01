package com.piecehk.werewolf.infra.output;

import com.piecehk.werewolf.agent.AgentJournal;
import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.Game;

import java.io.IOException;

public final class MatchSnapshotWriter {
    private final GodViewLogWriter godViewLogWriter = new GodViewLogWriter();
    private final AgentJournalWriter agentJournalWriter = new AgentJournalWriter();
    private final ReplayWriter replayWriter = new ReplayWriter();

    public synchronized void write(Game game, AgentJournal journal, MatchWorkspace workspace, Camp winner) {
        try {
            godViewLogWriter.write(game, workspace);
            agentJournalWriter.write(game, journal, workspace);
            replayWriter.write(game, journal, workspace, winner);
            replayWriter.writeMeta(game, workspace, winner);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write match snapshot", e);
        }
    }
}
