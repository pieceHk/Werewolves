package com.piecehk.werewolf.game;

import com.piecehk.werewolf.agent.ActionParser;
import com.piecehk.werewolf.agent.AgentJournal;
import com.piecehk.werewolf.agent.AgentOrchestrator;
import com.piecehk.werewolf.agent.ContextBuilder;
import com.piecehk.werewolf.agent.PromptBuilder;
import com.piecehk.werewolf.agent.llm.ChatOptions;
import com.piecehk.werewolf.agent.llm.LLMClient;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GameFactory;
import com.piecehk.werewolf.core.model.Player;
import com.piecehk.werewolf.core.model.RuleConfig;
import com.piecehk.werewolf.core.rule.NightResolver;
import com.piecehk.werewolf.core.rule.VoteResolver;
import com.piecehk.werewolf.core.rule.WinChecker;
import com.piecehk.werewolf.core.statemachine.GameStateMachine;
import com.piecehk.werewolf.game.player.LLMPlayerController;
import com.piecehk.werewolf.game.player.PlayerController;
import com.piecehk.werewolf.infra.output.MatchWorkspace;
import com.piecehk.werewolf.infra.output.MatchSnapshotWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MatchManager {
    public MatchResult runSingleMatch(long seed, int roundsCap, Path outputDir, LLMClient llmClient) throws IOException {
        long effectiveSeed = seed == 0 ? System.currentTimeMillis() : seed;
        Game game = GameFactory.standardNine(null, effectiveSeed, RuleConfig.defaults());
        MatchWorkspace workspace = MatchWorkspace.create(outputDir, game.matchId());
        AgentJournal journal = new AgentJournal();
        MatchSnapshotWriter snapshotWriter = new MatchSnapshotWriter();
        game.onEvent(changedGame -> snapshotWriter.write(changedGame, journal, workspace, null));
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            Map<Integer, PlayerController> controllers = new HashMap<>();
            for (Player player : game.players()) {
                controllers.put(player.seatNo(), new LLMPlayerController(
                        llmClient,
                        new PromptBuilder(),
                        new ActionParser(),
                        ChatOptions.defaults("qwen3-max").withTimeout(Duration.ofSeconds(60))
                ));
            }
            GameStateMachine stateMachine = new GameStateMachine();
            AgentOrchestrator orchestrator = new AgentOrchestrator(new ContextBuilder(), journal, controllers, executor);
            PhaseScheduler scheduler = new PhaseScheduler(orchestrator, stateMachine, new NightResolver(),
                    new VoteResolver(), new WinChecker());
            return new GameService(scheduler, stateMachine).run(game, journal, workspace, roundsCap);
        } finally {
            executor.shutdownNow();
        }
    }
}
