package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.agent.action.ActionType;
import com.piecehk.werewolf.agent.action.BadgeTransferAction;
import com.piecehk.werewolf.agent.action.SeerCheckAction;
import com.piecehk.werewolf.agent.action.SheriffRunAction;
import com.piecehk.werewolf.agent.action.SheriffVoteAction;
import com.piecehk.werewolf.agent.action.SpeakAction;
import com.piecehk.werewolf.agent.action.SpeechOrderAction;
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
import com.piecehk.werewolf.core.model.SpeechOrder;
import com.piecehk.werewolf.game.player.PlayerController;

import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        collectDiscussion(game, List.of());
    }

    public void collectDiscussion(Game game, List<Integer> recentDeaths) {
        for (Player player : discussionOrder(game, recentDeaths)) {
            AgentContext context = contextBuilder.build(game, player, null, journal.read(player.seatNo()));
            ParsedAction parsed = decide(player, context, ActionType.SPEAK, aliveTargetsExcept(game, player.seatNo()));
            String speech = parsed.action() instanceof SpeakAction action ? action.speech() : "";
            game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_SPOKE, game.roundNo(), GamePhase.DAY_DISCUSS,
                    player.seatNo(), player.role(), "白天发言", "座位" + player.seatNo() + "：" + speech));
            appendJournal(player, game, "白天发言", parsed);
            addWarningIfAny(game, parsed);
        }
        collectSheriffSummary(game);
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
            collectBadgeTransferIfNeeded(game, player);
        }
    }

    public void runSheriffElection(Game game) {
        if (!game.ruleConfig().sheriffEnabled()) {
            return;
        }
        game.addEvent(BasicGameEvent.publicEvent(EventType.SHERIFF_ELECTION_STARTED, game.roundNo(),
                GamePhase.DAY_SHERIFF_ELECTION, null, null, "警长竞选", "第1天警长竞选开始"));
        Map<Integer, Boolean> runDeclarations = new LinkedHashMap<>();
        for (Player player : game.alivePlayers()) {
            AgentContext context = contextBuilder.build(game, player, null, journal.read(player.seatNo()));
            ParsedAction parsed = decide(player, context, ActionType.SHERIFF_RUN, Set.of());
            boolean run = parsed.action() instanceof SheriffRunAction action && action.run();
            runDeclarations.put(player.seatNo(), run);
            game.addEvent(BasicGameEvent.publicEvent(EventType.SHERIFF_RUN_DECLARED, game.roundNo(),
                    GamePhase.DAY_SHERIFF_ELECTION, player.seatNo(), player.role(), "上警表态",
                    "座位" + player.seatNo() + (run ? " 上警参选" : " 不上警")));
            appendJournal(player, game, "上警表态", parsed);
        }

        Set<Integer> runSeats = runDeclarations.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (runSeats.isEmpty()) {
            electSheriff(game, null, "无人上警，本局无警长");
            return;
        }

        Set<Integer> activeCandidates = new LinkedHashSet<>(runSeats);
        for (Integer seat : runSeats.stream().sorted().toList()) {
            Player player = game.requirePlayer(seat);
            AgentContext context = contextBuilder.build(game, player, null, journal.read(player.seatNo()));
            ParsedAction parsed = decide(player, context, ActionType.SPEAK, activeCandidates);
            SpeakAction speak = parsed.action() instanceof SpeakAction action ? action : new SpeakAction("");
            game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_SPOKE, game.roundNo(),
                    GamePhase.DAY_SHERIFF_ELECTION, seat, player.role(), "警上发言",
                    "座位" + seat + "：" + speak.speech()));
            if (speak.withdraw()) {
                activeCandidates.remove(seat);
                game.addEvent(BasicGameEvent.publicEvent(EventType.SHERIFF_WITHDREW, game.roundNo(),
                        GamePhase.DAY_SHERIFF_ELECTION, seat, player.role(), "退水", "座位" + seat + " 退水"));
            }
            appendJournal(player, game, "警上发言", parsed);
        }

        if (activeCandidates.isEmpty()) {
            electSheriff(game, null, "所有上警玩家均退水，本局无警长");
            return;
        }
        if (activeCandidates.size() == 1) {
            electSheriff(game, activeCandidates.iterator().next(), "唯一候选人自动当选警长");
            return;
        }

        Set<Integer> voters = game.alivePlayers().stream()
                .map(Player::seatNo)
                .filter(seat -> !runSeats.contains(seat))
                .collect(Collectors.toSet());
        ElectionResult first = collectAndResolveSheriffVotes(game, voters, activeCandidates, false);
        if (first.electedSeat() != null) {
            electSheriff(game, first.electedSeat(), "座位" + first.electedSeat() + " 当选警长");
            return;
        }
        for (Integer seat : first.tiedSeats()) {
            Player player = game.requirePlayer(seat);
            AgentContext context = contextBuilder.build(game, player, null, journal.read(player.seatNo()));
            ParsedAction parsed = decide(player, context, ActionType.SPEAK, activeCandidates);
            String speech = parsed.action() instanceof SpeakAction action ? action.speech() : "";
            game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_SPOKE, game.roundNo(),
                    GamePhase.DAY_SHERIFF_ELECTION, seat, player.role(), "警长PK发言",
                    "座位" + seat + "：" + speech));
            appendJournal(player, game, "警长PK发言", parsed);
        }
        ElectionResult second = collectAndResolveSheriffVotes(game, voters, Set.copyOf(first.tiedSeats()), true);
        electSheriff(game, second.electedSeat(), second.electedSeat() == null
                ? "警长竞选仍平票，警徽流失"
                : "座位" + second.electedSeat() + " 当选警长");
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
                .orElse(null);
        game.addEvent(BasicGameEvent.privateEvent(EventType.WOLF_KILL_DECIDED, wolfAudience,
                game.roundNo(), GamePhase.NIGHT, null, RoleType.WEREWOLF, "① 狼人",
                target == null ? "本夜选择空刀" : "决议击杀座位" + target));
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
        Integer target = parsed.action() instanceof SeerCheckAction action ? action.targetSeat() : null;
        if (target == null) {
            game.addEvent(BasicGameEvent.privateEvent(EventType.SEER_CHECKED, Set.of(seer.seatNo()),
                    game.roundNo(), GamePhase.NIGHT, seer.seatNo(), seer.role(), "② 预言家",
                    "本夜选择不查验"));
        }
        return target;
    }

    private WitchAction collectWitchAction(Game game, Integer wolfTarget) {
        Player witch = game.playersByRole(RoleType.WITCH).stream().filter(Player::isAlive).findFirst().orElse(null);
        if (witch == null) {
            return new WitchAction(false, null);
        }
        AgentContext context = contextBuilder.build(game, witch, wolfTarget, journal.read(witch.seatNo()));
        ParsedAction parsed = decide(witch, context, ActionType.WITCH, aliveTargetsExcept(game, witch.seatNo()));
        appendJournal(witch, game, "女巫行动", parsed);
        WitchAction action = parsed.action() instanceof WitchAction parsedAction ? parsedAction : new WitchAction(false, null);
        if (!action.useAntidote() && action.poisonSeat() == null) {
            game.addEvent(BasicGameEvent.privateEvent(EventType.WITCH_ACTED, Set.of(witch.seatNo()),
                    game.roundNo(), GamePhase.NIGHT, witch.seatNo(), witch.role(), "③ 女巫",
                    "今晚被刀：" + wolfTarget + "；本夜选择不用药"));
        }
        return action;
    }

    private ParsedAction decide(Player player, AgentContext context, ActionType required, Set<Integer> validTargets) {
        return controllers.get(player.seatNo()).decide(context, required, validTargets, 100);
    }

    private List<Player> discussionOrder(Game game, List<Integer> recentDeaths) {
        Integer sheriffSeat = game.sheriffSeat();
        Player sheriff = sheriffSeat == null ? null : game.playerBySeat(sheriffSeat).filter(Player::isAlive).orElse(null);
        if (sheriff == null) {
            return game.alivePlayers();
        }
        Set<SpeechOrder> legalOrders = recentDeaths.size() == 1
                ? Set.of(SpeechOrder.DEAD_LEFT, SpeechOrder.DEAD_RIGHT)
                : Set.of(SpeechOrder.SHERIFF_LEFT, SpeechOrder.SHERIFF_RIGHT);
        AgentContext context = contextBuilder.build(game, sheriff, null, journal.read(sheriff.seatNo()));
        ParsedAction parsed = decide(sheriff, context, ActionType.SPEECH_ORDER, Set.of());
        SpeechOrder order = parsed.action() instanceof SpeechOrderAction action && legalOrders.contains(action.order())
                ? action.order()
                : legalOrders.stream().findFirst().orElse(SpeechOrder.SHERIFF_LEFT);
        game.addEvent(BasicGameEvent.publicEvent(EventType.SPEECH_ORDER_CHOSEN, game.roundNo(), GamePhase.DAY_DISCUSS,
                sheriff.seatNo(), sheriff.role(), "发言顺序", "警长选择：" + order));
        appendJournal(sheriff, game, "指定发言顺序", parsed);
        int anchor = switch (order) {
            case SHERIFF_LEFT, SHERIFF_RIGHT -> sheriff.seatNo();
            case DEAD_LEFT, DEAD_RIGHT -> recentDeaths.get(0);
        };
        boolean ascending = order == SpeechOrder.SHERIFF_LEFT || order == SpeechOrder.DEAD_LEFT;
        return orderedAliveFrom(game, anchor, ascending);
    }

    private List<Player> orderedAliveFrom(Game game, int anchorSeat, boolean ascending) {
        List<Player> result = new ArrayList<>();
        for (int step = 1; step <= 9; step++) {
            int seat = ascending
                    ? ((anchorSeat + step - 1) % 9) + 1
                    : ((anchorSeat - step - 1 + 18) % 9) + 1;
            game.playerBySeat(seat).filter(Player::isAlive).ifPresent(result::add);
        }
        return result;
    }

    private void collectSheriffSummary(Game game) {
        Integer sheriffSeat = game.sheriffSeat();
        if (sheriffSeat == null) {
            return;
        }
        Player sheriff = game.playerBySeat(sheriffSeat).filter(Player::isAlive).orElse(null);
        if (sheriff == null) {
            return;
        }
        AgentContext context = contextBuilder.build(game, sheriff, null, journal.read(sheriff.seatNo()));
        ParsedAction parsed = decide(sheriff, context, ActionType.SPEAK, aliveTargetsExcept(game, sheriff.seatNo()));
        String speech = parsed.action() instanceof SpeakAction action ? action.speech() : "";
        game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_SPOKE, game.roundNo(), GamePhase.DAY_DISCUSS,
                sheriff.seatNo(), sheriff.role(), "警长归票", "座位" + sheriffSeat + "：" + speech));
        appendJournal(sheriff, game, "警长归票", parsed);
    }

    private ElectionResult collectAndResolveSheriffVotes(Game game, Set<Integer> voters, Set<Integer> candidates, boolean revote) {
        if (voters.isEmpty()) {
            return new ElectionResult(null, candidates.stream().sorted().toList());
        }
        Map<Integer, Integer> votes = new LinkedHashMap<>();
        for (Integer voterSeat : voters.stream().sorted().toList()) {
            Player voter = game.requirePlayer(voterSeat);
            AgentContext context = contextBuilder.build(game, voter, null, journal.read(voterSeat));
            ParsedAction parsed = decide(voter, context, ActionType.SHERIFF_VOTE, candidates);
            Integer target = parsed.action() instanceof SheriffVoteAction action ? action.targetSeat() : null;
            votes.put(voterSeat, target);
            game.addEvent(BasicGameEvent.publicEvent(EventType.SHERIFF_VOTE_CAST, game.roundNo(),
                    GamePhase.DAY_SHERIFF_ELECTION, voterSeat, voter.role(), revote ? "警长重投" : "警长投票",
                    "座位" + voterSeat + " 投票给 " + (target == null ? "弃票" : "座位" + target)));
            appendJournal(voter, game, revote ? "警长重投" : "警长投票", parsed);
        }
        Map<Integer, Long> counts = votes.values().stream()
                .filter(target -> target != null)
                .collect(Collectors.groupingBy(target -> target, Collectors.counting()));
        if (counts.isEmpty()) {
            return new ElectionResult(null, List.of());
        }
        long max = counts.values().stream().max(Long::compareTo).orElse(0L);
        List<Integer> tied = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        return new ElectionResult(tied.size() == 1 ? tied.get(0) : null, tied);
    }

    private void electSheriff(Game game, Integer sheriffSeat, String text) {
        game.setSheriffSeat(sheriffSeat);
        game.addEvent(BasicGameEvent.publicEvent(EventType.SHERIFF_ELECTED, game.roundNo(),
                GamePhase.DAY_SHERIFF_ELECTION, sheriffSeat,
                sheriffSeat == null ? null : game.requirePlayer(sheriffSeat).role(), "警长结果", text));
    }

    private void collectBadgeTransferIfNeeded(Game game, Player deadPlayer) {
        if (game.sheriffSeat() == null || !game.sheriffSeat().equals(deadPlayer.seatNo())) {
            return;
        }
        Set<Integer> aliveTargets = game.alivePlayers().stream().map(Player::seatNo).collect(Collectors.toSet());
        AgentContext context = contextBuilder.build(game, deadPlayer, null, journal.read(deadPlayer.seatNo()));
        ParsedAction parsed = decide(deadPlayer, context, ActionType.BADGE_TRANSFER, aliveTargets);
        Integer target = parsed.action() instanceof BadgeTransferAction action ? action.targetSeat() : null;
        game.setSheriffSeat(target);
        game.addEvent(BasicGameEvent.publicEvent(EventType.BADGE_TRANSFERRED, game.roundNo(), game.phase(),
                deadPlayer.seatNo(), deadPlayer.role(), "警徽处理",
                target == null ? "座位" + deadPlayer.seatNo() + " 撕毁警徽"
                        : "座位" + deadPlayer.seatNo() + " 将警徽移交给座位" + target));
        appendJournal(deadPlayer, game, "警徽处理", parsed);
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

    private record ElectionResult(Integer electedSeat, List<Integer> tiedSeats) {
    }
}
