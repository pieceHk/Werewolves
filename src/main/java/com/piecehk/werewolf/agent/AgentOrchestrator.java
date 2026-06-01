package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.agent.action.ActionType;
import com.piecehk.werewolf.agent.action.SeerCheckAction;
import com.piecehk.werewolf.agent.action.SpeakAction;
import com.piecehk.werewolf.agent.action.VoteAction;
import com.piecehk.werewolf.agent.action.WitchAction;
import com.piecehk.werewolf.agent.action.WolfKillAction;
import com.piecehk.werewolf.core.event.BasicGameEvent;
import com.piecehk.werewolf.core.event.EventType;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GamePhase;
import com.piecehk.werewolf.core.model.NightActions;
import com.piecehk.werewolf.core.model.Player;
import com.piecehk.werewolf.core.model.RoleType;
import com.piecehk.werewolf.game.player.PlayerController;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public final class AgentOrchestrator {
    private final ContextBuilder contextBuilder;
    private final AgentJournal journal;
    private final Map<Integer, PlayerController> controllers;
    private final ExecutorService executor;

    public AgentOrchestrator(ContextBuilder contextBuilder, AgentJournal journal,
                             Map<Integer, PlayerController> controllers, ExecutorService executor) {
        this.contextBuilder = contextBuilder;
        this.journal = journal;
        this.controllers = Map.copyOf(controllers);
        this.executor = executor;
    }

    public NightActions collectNightActions(Game game) {
        Integer wolfTarget = collectWolfTarget(game);
        Integer seerTarget = collectSeerTarget(game, wolfTarget);
        WitchAction witchAction = collectWitchAction(game, wolfTarget);
        return new NightActions(wolfTarget, seerTarget, witchAction.useAntidote(), witchAction.poisonSeat());
    }

    public void collectDiscussion(Game game) {
        for (Player player : game.alivePlayers()) {
            AgentContext context = contextBuilder.build(game, player, null, journal.read(player.seatNo()));
            ParsedAction parsed = decide(player, context, ActionType.SPEAK, aliveTargetsExcept(game, player.seatNo()));
            String speech = parsed.action() instanceof SpeakAction action ? action.speech() : "";
            game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_SPOKE, game.roundNo(), GamePhase.DAY_DISCUSS,
                    player.seatNo(), player.role(), "白天发言", "座位" + player.seatNo() + "：" + speech));
            appendJournal(player, game, "白天发言", parsed);
            addWarningIfAny(game, parsed);
        }
    }

    public Map<Integer, Integer> collectVotes(Game game) {
        List<CompletableFuture<Map.Entry<Integer, Integer>>> futures = game.alivePlayers().stream()
                .map(player -> CompletableFuture.supplyAsync(() -> {
                    AgentContext context = contextBuilder.build(game, player, null, journal.read(player.seatNo()));
                    ParsedAction parsed = decide(player, context, ActionType.VOTE, aliveTargetsExcept(game, player.seatNo()));
                    Integer target = parsed.action() instanceof VoteAction action ? action.targetSeat() : null;
                    appendJournal(player, game, "投票", parsed);
                    return Map.entry(player.seatNo(), target == null ? 0 : target);
                }, executor))
                .toList();
        Map<Integer, Integer> votes = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<Integer, Integer>> future : futures) {
            Map.Entry<Integer, Integer> entry = future.join();
            votes.put(entry.getKey(), entry.getValue() == 0 ? null : entry.getValue());
            game.addEvent(BasicGameEvent.publicEvent(EventType.VOTE_CAST, game.roundNo(), GamePhase.DAY_VOTE,
                    entry.getKey(), game.requirePlayer(entry.getKey()).role(), "投票",
                    "座位" + entry.getKey() + " 投票给 " + (entry.getValue() == 0 ? "弃票" : "座位" + entry.getValue())));
        }
        return votes;
    }

    public void collectLastWords(Game game, List<Integer> deadSeats) {
        for (Integer seat : deadSeats) {
            Player player = game.requirePlayer(seat);
            AgentContext context = contextBuilder.build(game, player, null, journal.read(player.seatNo()));
            ParsedAction parsed = decide(player, context, ActionType.SPEAK, aliveTargetsExcept(game, player.seatNo()));
            String speech = parsed.action() instanceof SpeakAction action ? action.speech() : "";
            game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_SPOKE, game.roundNo(), game.phase(),
                    player.seatNo(), player.role(), "遗言", "座位" + seat + "遗言：" + speech));
            appendJournal(player, game, "遗言", parsed);
        }
    }

    private Integer collectWolfTarget(Game game) {
        List<Player> wolves = game.playersByRole(RoleType.WEREWOLF).stream().filter(Player::isAlive).toList();
        if (wolves.isEmpty()) {
            return null;
        }
        Set<Integer> wolfAudience = wolves.stream().map(Player::seatNo).collect(Collectors.toSet());
        Map<Integer, Long> nominations = new HashMap<>();
        for (int wolfRound = 1; wolfRound <= 2; wolfRound++) {
            for (Player wolf : wolves) {
                AgentContext context = contextBuilder.build(game, wolf, null, journal.read(wolf.seatNo()));
                ActionType required = wolfRound == 2 ? ActionType.WOLF_KILL : ActionType.SPEAK;
                ParsedAction parsed = decide(wolf, context, required, aliveNonWolfTargets(game));
                if (parsed.action() instanceof SpeakAction speech) {
                    game.addEvent(BasicGameEvent.privateEvent(EventType.WOLF_DISCUSSION, wolfAudience,
                            game.roundNo(), GamePhase.NIGHT, wolf.seatNo(), wolf.role(), "狼人商讨",
                            "座位" + wolf.seatNo() + "：" + speech.speech()));
                }
                if (parsed.action() instanceof WolfKillAction kill && kill.targetSeat() != null) {
                    nominations.merge(kill.targetSeat(), 1L, Long::sum);
                }
                appendJournal(wolf, game, "狼人夜晚", parsed);
            }
        }
        Integer target = nominations.entrySet().stream()
                .max(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElseGet(() -> aliveNonWolfTargets(game).stream().findFirst().orElse(null));
        game.addEvent(BasicGameEvent.privateEvent(EventType.WOLF_KILL_DECIDED, wolfAudience,
                game.roundNo(), GamePhase.NIGHT, null, RoleType.WEREWOLF, "狼人决议", "决议击杀座位" + target));
        return target;
    }

    private Integer collectSeerTarget(Game game, Integer wolfTarget) {
        Player seer = game.playersByRole(RoleType.SEER).stream().filter(Player::isAlive).findFirst().orElse(null);
        if (seer == null) {
            return null;
        }
        AgentContext context = contextBuilder.build(game, seer, wolfTarget, journal.read(seer.seatNo()));
        ParsedAction parsed = decide(seer, context, ActionType.SEER_CHECK, aliveTargetsExcept(game, seer.seatNo()));
        appendJournal(seer, game, "预言家查验", parsed);
        return parsed.action() instanceof SeerCheckAction action ? action.targetSeat() : null;
    }

    private WitchAction collectWitchAction(Game game, Integer wolfTarget) {
        Player witch = game.playersByRole(RoleType.WITCH).stream().filter(Player::isAlive).findFirst().orElse(null);
        if (witch == null) {
            return new WitchAction(false, null);
        }
        AgentContext context = contextBuilder.build(game, witch, wolfTarget, journal.read(witch.seatNo()));
        ParsedAction parsed = decide(witch, context, ActionType.WITCH, aliveTargetsExcept(game, witch.seatNo()));
        appendJournal(witch, game, "女巫行动", parsed);
        return parsed.action() instanceof WitchAction action ? action : new WitchAction(false, null);
    }

    private ParsedAction decide(Player player, AgentContext context, ActionType required, Set<Integer> validTargets) {
        return controllers.get(player.seatNo()).decide(context, required, validTargets, 100);
    }

    private Set<Integer> aliveTargetsExcept(Game game, int selfSeat) {
        return game.alivePlayers().stream().map(Player::seatNo).filter(seat -> seat != selfSeat).collect(Collectors.toSet());
    }

    private Set<Integer> aliveNonWolfTargets(Game game) {
        return game.alivePlayers().stream()
                .filter(player -> player.role() != RoleType.WEREWOLF)
                .map(Player::seatNo)
                .collect(Collectors.toSet());
    }

    private void appendJournal(Player player, Game game, String phase, ParsedAction parsed) {
        journal.append(player.seatNo(), "## 第" + game.roundNo() + "轮 · " + phase
                + "\nreasoning：" + parsed.reasoning()
                + "\naction：" + parsed.action());
    }

    private void addWarningIfAny(Game game, ParsedAction parsed) {
        if (parsed.warning() != null && !parsed.warning().isBlank()) {
            game.addEvent(BasicGameEvent.publicEvent(EventType.SYSTEM_NOTE, game.roundNo(), game.phase(),
                    null, null, "系统备注", parsed.warning()));
        }
    }
}
