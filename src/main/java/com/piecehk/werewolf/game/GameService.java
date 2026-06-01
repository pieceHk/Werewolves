package com.piecehk.werewolf.game;

import com.piecehk.werewolf.agent.AgentJournal;
import com.piecehk.werewolf.core.event.BasicGameEvent;
import com.piecehk.werewolf.core.event.EventType;
import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GamePhase;
import com.piecehk.werewolf.core.statemachine.GameStateMachine;
import com.piecehk.werewolf.infra.output.MatchWorkspace;

public final class GameService {
    private final PhaseScheduler scheduler;
    private final GameStateMachine stateMachine;

    public GameService(PhaseScheduler scheduler, GameStateMachine stateMachine) {
        this.scheduler = scheduler;
        this.stateMachine = stateMachine;
    }

    public MatchResult run(Game game, AgentJournal journal, MatchWorkspace workspace, int roundsCap) {
        game.addEvent(BasicGameEvent.publicEvent(EventType.GAME_STARTED, game.roundNo(), GamePhase.PREPARING,
                null, null, "游戏开始", "对局开始，seed=" + game.randomSeed()));
        Camp winner = null;
        while (winner == null && game.roundNo() <= roundsCap) {
            winner = scheduler.runRound(game).orElse(null);
        }
        if (winner == null) {
            winner = Camp.GOOD;
            game.addEvent(BasicGameEvent.publicEvent(EventType.SYSTEM_NOTE, game.roundNo(), game.phase(),
                    null, null, "轮数上限", "达到 rounds-cap，默认按好人胜处理"));
        }
        stateMachine.transition(game, GamePhase.GAME_OVER);
        game.addEvent(BasicGameEvent.publicEvent(EventType.GAME_OVER, game.roundNo(), GamePhase.GAME_OVER,
                null, null, "游戏结束", "胜利阵营：" + winner));
        return new MatchResult(game, journal, workspace, winner);
    }
}
