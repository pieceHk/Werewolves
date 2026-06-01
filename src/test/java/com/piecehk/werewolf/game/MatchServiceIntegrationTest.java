package com.piecehk.werewolf.game;

import com.piecehk.werewolf.agent.llm.MockLLMClient;
import com.piecehk.werewolf.infra.output.AgentJournalWriter;
import com.piecehk.werewolf.infra.output.GodViewLogWriter;
import com.piecehk.werewolf.infra.output.ReplayWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MatchServiceIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void runsFullMatchAndWritesArtifacts() throws Exception {
        MatchResult result = new MatchManager().runSingleMatch(12345L, 8, tempDir, MockLLMClient.deterministic());
        new GodViewLogWriter().write(result.game(), result.workspace());
        new AgentJournalWriter().write(result.game(), result.journal(), result.workspace());
        ReplayWriter replayWriter = new ReplayWriter();
        replayWriter.write(result.game(), result.journal(), result.workspace(), result.winner());
        replayWriter.writeMeta(result.game(), result.workspace(), result.winner());

        assertThat(result.game().phase().name()).isEqualTo("GAME_OVER");
        assertThat(Files.exists(result.workspace().godView())).isTrue();
        assertThat(Files.exists(result.workspace().replay())).isTrue();
        assertThat(Files.exists(result.workspace().meta())).isTrue();
        assertThat(Files.list(result.workspace().agentsDir()).count()).isEqualTo(9);
    }
}
