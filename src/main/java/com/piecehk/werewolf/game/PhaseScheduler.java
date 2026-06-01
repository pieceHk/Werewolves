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
import com.piecehk.werewolf.core.rule.WitchRules;
import com.piecehk.werewolf.core.rule.NightOutcome;
import com.piecehk.werewolf.core.rule.NightResolver;
import com.piecehk.werewolf.core.rule.VoteOutcome;
import com.piecehk.werewolf.core.rule.VoteResolver;
import com.piecehk.werewolf.core.rule.WinChecker;
import com.piecehk.werewolf.core.statemachine.GameStateMachine;
import com.piecehk.werewolf.core.score.ScoreService;
import com.piecehk.werewolf.core.score.ScoreboardRound;

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
    private final ScoreService scoreService = new ScoreService();

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

        NightActions actions = sanitizeNightActions(game, orchestrator.collectNightActions(game));
        NightOutcome nightOutcome = nightResolver.resolve(game, actions);
        recordPrivateNightEvents(game, actions);
        applyNightOutcome(game, nightOutcome);
        Optional<Camp> winner = winChecker.check(game);
        if (winner.isPresent()) {
            return winner;
        }

        if (game.roundNo() == 1 && game.ruleConfig().sheriffEnabled()) {
            stateMachine.transition(game, GamePhase.DAY_SHERIFF_ELECTION);
            orchestrator.runSheriffElection(game);
        }

        stateMachine.transition(game, GamePhase.DAY_ANNOUNCE);
        List<Integer> deaths = nightOutcome.deaths().keySet().stream().toList();
        game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_DIED, game.roundNo(), GamePhase.DAY_ANNOUNCE,
                null, null, "夜晚死亡", deaths.isEmpty() ? "昨夜平安夜，无人死亡" : "昨夜死亡：" + deaths));
        if (game.roundNo() == 1 && game.ruleConfig().allowFirstNightLastWords()) {
            orchestrator.collectLastWords(game, deaths);
        } else {
            destroySheriffBadgeOnSilentDeath(game, deaths);
        }
        winner = winChecker.check(game);
        if (winner.isPresent()) {
            return winner;
        }

        stateMachine.transition(game, GamePhase.DAY_DISCUSS);
        orchestrator.collectDiscussion(game, deaths);

        stateMachine.transition(game, GamePhase.DAY_VOTE);
        Map<Integer, Integer> votes = orchestrator.collectVotes(game);
        VoteOutcome voteOutcome = voteResolver.resolve(votes, game.ruleConfig(), false, game.sheriffSeat());
        if (voteOutcome.requiresRevote()) {
            Set<Integer> tied = Set.copyOf(voteOutcome.tiedSeats());
            Map<Integer, Integer> revoteVotes = votes.entrySet().stream()
                    .filter(entry -> tied.contains(entry.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            voteOutcome = voteResolver.resolve(revoteVotes, game.ruleConfig(), true, game.sheriffSeat());
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
        recordScoreboard(game, null);
        return winChecker.check(game);
    }

    public void recordScoreboard(Game game, Camp winner) {
        ScoreboardRound scoreboard = scoreService.calculate(game, winner);
        game.addScoreboard(scoreboard);
        game.addEvent(BasicGameEvent.publicEvent(EventType.SYSTEM_NOTE, game.roundNo(), game.phase(),
                null, null, "玩家表现分", scoreService.formatTable(scoreboard)));
    }

    private void destroySheriffBadgeOnSilentDeath(Game game, List<Integer> deaths) {
        if (game.sheriffSeat() != null && deaths.contains(game.sheriffSeat())) {
            Integer oldSheriff = game.sheriffSeat();
            game.setSheriffSeat(null);
            game.addEvent(BasicGameEvent.publicEvent(EventType.BADGE_TRANSFERRED, game.roundNo(), game.phase(),
                    oldSheriff, game.requirePlayer(oldSheriff).role(), "警徽处理",
                    "警长夜晚出局且无遗言，警徽默认撕毁"));
        }
    }

    private void recordPrivateNightEvents(Game game, NightActions actions) {
        if (actions.seerTarget() != null) {
            Player seer = game.playersByRole(RoleType.SEER).stream().findFirst().orElse(null);
            if (seer != null) {
                CheckResult result = game.requirePlayer(actions.seerTarget()).role() == RoleType.WEREWOLF
                        ? CheckResult.WEREWOLF : CheckResult.GOOD;
                game.addEvent(BasicGameEvent.privateEvent(EventType.SEER_CHECKED, Set.of(seer.seatNo()),
                        game.roundNo(), GamePhase.NIGHT, seer.seatNo(), seer.role(), "② 预言家",
                        "查验座位" + actions.seerTarget() + " → " + result));
            }
        }
        Player witch = game.playersByRole(RoleType.WITCH).stream().findFirst().orElse(null);
        if (witch != null) {
            game.addEvent(BasicGameEvent.privateEvent(EventType.WITCH_ACTED, Set.of(witch.seatNo()),
                    game.roundNo(), GamePhase.NIGHT, witch.seatNo(), witch.role(), "③ 女巫",
                    "被刀：座位" + actions.wolfTarget()
                            + " | 解药：" + (actions.witchSave() ? "使用(救" + actions.wolfTarget() + ")" : "未使用")
                            + " | 毒药：" + (actions.witchPoison() == null ? "未使用" : "使用(毒" + actions.witchPoison() + ")")
                            + " | 剩余：解药" + (actions.witchSave() ? 0 : (game.witchState().antidoteAvailable() ? 1 : 0))
                            + " 毒药" + (actions.witchPoison() == null ? (game.witchState().poisonAvailable() ? 1 : 0) : 0)));
        }
    }

    private NightActions sanitizeNightActions(Game game, NightActions actions) {
        Integer wolfTarget = validAliveTarget(game, actions.wolfTarget()) ? actions.wolfTarget() : null;
        Integer seerTarget = validAliveTarget(game, actions.seerTarget()) ? actions.seerTarget() : null;
        boolean witchSave = actions.witchSave();
        Integer witchPoison = validAliveTarget(game, actions.witchPoison()) ? actions.witchPoison() : null;

        if (actions.wolfTarget() != null && wolfTarget == null) {
            game.addEvent(BasicGameEvent.publicEvent(EventType.SYSTEM_NOTE, game.roundNo(), GamePhase.NIGHT,
                    null, null, "动作降级", "狼人击杀目标无效，已改为无人被刀"));
        }
        if (actions.seerTarget() != null && seerTarget == null) {
            seerSystemNote(game, "预言家查验目标无效，已忽略");
        }
        if (actions.witchPoison() != null && witchPoison == null) {
            witchSystemNote(game, "女巫毒药目标无效，已忽略");
        }
        if (witchSave && wolfTarget == null) {
            witchSystemNote(game, "女巫选择救人但今晚没有有效刀口，已忽略解药");
            witchSave = false;
        }
        if (witchSave && !game.witchState().antidoteAvailable()) {
            witchSystemNote(game, "女巫解药已不可用，已忽略解药");
            witchSave = false;
        }
        if (witchPoison != null && !game.witchState().poisonAvailable()) {
            witchSystemNote(game, "女巫毒药已不可用，已忽略毒药");
            witchPoison = null;
        }
        if (witchSave && witchPoison != null && !WitchRules.WITCH_BOTH_POTIONS_SAME_NIGHT) {
            witchSystemNote(game, "女巫不可同晚双药，已保留解药并忽略毒药");
            witchPoison = null;
        }
        if (witchSave && wolfTarget != null && isForbiddenWitchSelfSave(game, wolfTarget)) {
            witchSystemNote(game, "女巫不可自救，解药无效");
            witchSave = false;
        }
        return new NightActions(wolfTarget, seerTarget, witchSave, witchPoison);
    }

    private boolean validAliveTarget(Game game, Integer target) {
        return target == null || game.playerBySeat(target).map(Player::isAlive).orElse(false);
    }

    private boolean isForbiddenWitchSelfSave(Game game, Integer wolfTarget) {
        Player witch = game.playersByRole(RoleType.WITCH).stream().findFirst().orElse(null);
        if (witch == null || witch.seatNo() != wolfTarget) {
            return false;
        }
        return !WitchRules.WITCH_CAN_SELF_SAVE;
    }

    private void seerSystemNote(Game game, String text) {
        Player seer = game.playersByRole(RoleType.SEER).stream().findFirst().orElse(null);
        if (seer != null) {
            game.addEvent(BasicGameEvent.privateEvent(EventType.SYSTEM_NOTE, Set.of(seer.seatNo()),
                    game.roundNo(), GamePhase.NIGHT, null, null, "动作降级", text));
        }
    }

    private void witchSystemNote(Game game, String text) {
        Player witch = game.playersByRole(RoleType.WITCH).stream().findFirst().orElse(null);
        if (witch != null) {
            game.addEvent(BasicGameEvent.privateEvent(EventType.SYSTEM_NOTE, Set.of(witch.seatNo()),
                    game.roundNo(), GamePhase.NIGHT, null, null, "动作降级", text));
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
