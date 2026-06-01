package com.piecehk.werewolf.game;

import com.piecehk.werewolf.agent.AgentOrchestrator;
import com.piecehk.werewolf.core.event.BasicGameEvent;
import com.piecehk.werewolf.core.event.EventType;
import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.CheckResult;
import com.piecehk.werewolf.core.model.DeathCause;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GamePhase;
import com.piecehk.werewolf.core.model.NightActions;
import com.piecehk.werewolf.core.model.Player;
import com.piecehk.werewolf.core.model.RoleType;
import com.piecehk.werewolf.core.rule.NightOutcome;
import com.piecehk.werewolf.core.rule.NightResolver;
import com.piecehk.werewolf.core.rule.VoteOutcome;
import com.piecehk.werewolf.core.rule.VoteResolver;
import com.piecehk.werewolf.core.rule.WinChecker;
import com.piecehk.werewolf.core.statemachine.GameStateMachine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class PhaseScheduler {
    private final AgentOrchestrator orchestrator;
    private final GameStateMachine stateMachine;
    private final NightResolver nightResolver;
    private final VoteResolver voteResolver;
    private final WinChecker winChecker;

    public PhaseScheduler(AgentOrchestrator orchestrator, GameStateMachine stateMachine,
                          NightResolver nightResolver, VoteResolver voteResolver, WinChecker winChecker) {
        this.orchestrator = orchestrator;
        this.stateMachine = stateMachine;
        this.nightResolver = nightResolver;
        this.voteResolver = voteResolver;
        this.winChecker = winChecker;
    }

    public Optional<Camp> runRound(Game game) {
        if (game.phase() == GamePhase.PREPARING || game.phase() == GamePhase.DAY_VOTE) {
            if (game.phase() == GamePhase.DAY_VOTE) {
                game.nextRound();
            }
            stateMachine.transition(game, GamePhase.NIGHT);
        }

        NightActions actions = orchestrator.collectNightActions(game);
        NightOutcome nightOutcome = nightResolver.resolve(game, actions);
        recordPrivateNightEvents(game, actions);
        applyNightOutcome(game, nightOutcome);
        Optional<Camp> winner = winChecker.check(game);
        if (winner.isPresent()) {
            return winner;
        }

        stateMachine.transition(game, GamePhase.DAY_ANNOUNCE);
        List<Integer> deaths = nightOutcome.deaths().keySet().stream().toList();
        game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_DIED, game.roundNo(), GamePhase.DAY_ANNOUNCE,
                null, null, "夜晚死亡", deaths.isEmpty() ? "昨夜平安夜，无人死亡" : "昨夜死亡：" + deaths));
        orchestrator.collectLastWords(game, deaths);
        winner = winChecker.check(game);
        if (winner.isPresent()) {
            return winner;
        }

        stateMachine.transition(game, GamePhase.DAY_DISCUSS);
        orchestrator.collectDiscussion(game);

        stateMachine.transition(game, GamePhase.DAY_VOTE);
        Map<Integer, Integer> votes = orchestrator.collectVotes(game);
        VoteOutcome voteOutcome = voteResolver.resolve(votes, game.ruleConfig(), false);
        if (voteOutcome.requiresRevote()) {
            Set<Integer> tied = Set.copyOf(voteOutcome.tiedSeats());
            Map<Integer, Integer> revoteVotes = votes.entrySet().stream()
                    .filter(entry -> tied.contains(entry.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            voteOutcome = voteResolver.resolve(revoteVotes, game.ruleConfig(), true);
        }
        if (voteOutcome.exiledSeat() != null) {
            Player exiled = game.requirePlayer(voteOutcome.exiledSeat());
            exiled.die();
            game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_EXILED, game.roundNo(), GamePhase.DAY_VOTE,
                    null, null, "放逐", "座位" + exiled.seatNo() + " 被放逐"));
            orchestrator.collectLastWords(game, List.of(exiled.seatNo()));
        } else {
            game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_EXILED, game.roundNo(), GamePhase.DAY_VOTE,
                    null, null, "放逐", "本轮无人出局"));
        }
        return winChecker.check(game);
    }

    private void recordPrivateNightEvents(Game game, NightActions actions) {
        if (actions.seerTarget() != null) {
            Player seer = game.playersByRole(RoleType.SEER).stream().findFirst().orElse(null);
            if (seer != null) {
                CheckResult result = game.requirePlayer(actions.seerTarget()).role() == RoleType.WEREWOLF
                        ? CheckResult.WEREWOLF : CheckResult.GOOD;
                game.addEvent(BasicGameEvent.privateEvent(EventType.SEER_CHECKED, Set.of(seer.seatNo()),
                        game.roundNo(), GamePhase.NIGHT, seer.seatNo(), seer.role(), "预言家查验",
                        "查验座位" + actions.seerTarget() + " → " + result));
            }
        }
        Player witch = game.playersByRole(RoleType.WITCH).stream().findFirst().orElse(null);
        if (witch != null) {
            game.addEvent(BasicGameEvent.privateEvent(EventType.WITCH_ACTED, Set.of(witch.seatNo()),
                    game.roundNo(), GamePhase.NIGHT, witch.seatNo(), witch.role(), "女巫行动",
                    "今晚被刀：" + actions.wolfTarget()
                            + "；解药=" + actions.witchSave()
                            + "；毒药目标=" + actions.witchPoison()));
        }
    }

    private void applyNightOutcome(Game game, NightOutcome outcome) {
        if (outcome.saved()) {
            game.witchState().consumeAntidote();
        }
        if (outcome.poisonTarget() != null) {
            game.witchState().consumePoison();
        }
        for (Map.Entry<Integer, DeathCause> death : outcome.deaths().entrySet()) {
            Player player = game.requirePlayer(death.getKey());
            player.die();
            game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_DIED, game.roundNo(), GamePhase.NIGHT,
                    null, null, "玩家死亡", "座位" + player.seatNo() + " 死亡，原因：" + death.getValue()));
        }
    }
}
