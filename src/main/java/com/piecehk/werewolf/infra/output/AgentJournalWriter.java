package com.piecehk.werewolf.infra.output;

import com.piecehk.werewolf.agent.AgentJournal;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.Player;

import java.io.IOException;
import java.nio.file.Files;

public final class AgentJournalWriter {
    public void write(Game game, AgentJournal journal, MatchWorkspace workspace) throws IOException {
        for (Player player : game.players()) {
            String content = "# 座位" + player.seatNo() + " Agent 记忆\n\n"
                    + "身份：" + player.role().displayName() + "\n\n"
                    + journal.read(player.seatNo()) + "\n";
            Files.writeString(workspace.agentsDir().resolve("seat-" + player.seatNo() + ".md"), content);
        }
    }
}
