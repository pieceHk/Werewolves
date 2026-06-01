package com.piecehk.werewolf.core.score;

import com.piecehk.werewolf.core.event.EventType;
import com.piecehk.werewolf.core.event.GameEvent;
import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.DeathCause;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.Player;
import com.piecehk.werewolf.core.model.RoleType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScoreService {
    private static final Pattern VOTE = Pattern.compile("座位(\\d+) 投票给 (?:座位(\\d+)|弃票)");
    private static final Pattern SEER = Pattern.compile("查验座位(\\d+) → (WEREWOLF|GOOD)");
    private static final Pattern DEATH = Pattern.compile("座位(\\d+) 死亡，原因：(\\w+)");

    private final ScoreConfig config;

    public ScoreService() {
        this(ScoreConfig.defaults());
    }

    public ScoreService(ScoreConfig config) {
        this.config = config;
    }

    public ScoreboardRound calculate(Game game, Camp winner) {
        Map<Integer, Double> scores = new HashMap<>();
        for (Player player : game.players()) {
            double survive = player.isAlive() ? Math.min(config.surviveCap(), game.roundNo() * config.survivePerRound()) : 0.0;
            scores.put(player.seatNo(), config.base() + survive);
        }
        for (GameEvent event : game.eventLog()) {
            applyEvent(game, scores, event);
        }
        if (winner != null) {
            for (Player player : game.players()) {
                if (player.role().camp() == winner) {
                    add(scores, player.seatNo(), config.winBonus());
                }
            }
        }
        List<PlayerScore> rows = new ArrayList<>();
        Map<Integer, Double> previous = lastScores(game);
        for (Player player : game.players()) {
            double score = clamp(round(scores.get(player.seatNo())));
            rows.add(new PlayerScore(0, player.seatNo(), player.role(), player.role().camp(), player.status(),
                    score, round(score - previous.getOrDefault(player.seatNo(), config.base()))));
        }
        rows.sort(Comparator.comparingDouble(PlayerScore::score).reversed().thenComparingInt(PlayerScore::seat));
        List<PlayerScore> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            PlayerScore row = rows.get(i);
            ranked.add(new PlayerScore(i + 1, row.seat(), row.role(), row.camp(), row.status(), row.score(), row.delta()));
        }
        return new ScoreboardRound(game.roundNo(), ranked);
    }

    public String formatTable(ScoreboardRound scoreboard) {
        StringBuilder sb = new StringBuilder();
        sb.append("════════════════ 第 ").append(scoreboard.roundNo())
                .append(" 轮结束 · 玩家表现分（满分10，越高越好） ════════════════\n");
        sb.append("| 排名 | 座位 | 角色 | 阵营 | 状态 | 本轮累计得分 | 较上轮 |\n");
        sb.append("|----|----|------|----|-----|--------|------|\n");
        for (PlayerScore score : scoreboard.scores()) {
            sb.append("| ").append(score.rank())
                    .append(" | ").append(score.seat())
                    .append(" | ").append(score.role().displayName())
                    .append(" | ").append(score.camp() == Camp.WEREWOLF ? "狼" : "好人")
                    .append(" | ").append(score.status())
                    .append(" | ").append(String.format(java.util.Locale.ROOT, "%.1f", score.score()))
                    .append(" | ").append(formatDelta(score.delta()))
                    .append(" |\n");
        }
        sb.append("说明：起始分5.0；当前为客观项累计，LLM裁判默认关闭。");
        return sb.toString();
    }

    private void applyEvent(Game game, Map<Integer, Double> scores, GameEvent event) {
        if (event.type() == EventType.VOTE_CAST) {
            Matcher matcher = VOTE.matcher(event.text());
            if (matcher.find()) {
                int voter = Integer.parseInt(matcher.group(1));
                String targetRaw = matcher.group(2);
                if (targetRaw == null) {
                    add(scores, voter, config.abstain());
                    return;
                }
                int target = Integer.parseInt(targetRaw);
                Player voterPlayer = game.requirePlayer(voter);
                Player targetPlayer = game.requirePlayer(target);
                if (voterPlayer.role().camp() == Camp.GOOD) {
                    add(scores, voter, targetPlayer.role() == RoleType.WEREWOLF ? config.voteHitWolf() : config.voteMiss());
                }
            }
        }
        if (event.type() == EventType.SEER_CHECKED && event.publisherSeat() != null) {
            Matcher matcher = SEER.matcher(event.text());
            if (matcher.find()) {
                add(scores, event.publisherSeat(), "WEREWOLF".equals(matcher.group(2))
                        ? config.seerKillHit() : config.seerClearCorrect());
            }
        }
        if (event.type() == EventType.PLAYER_DIED) {
            Matcher matcher = DEATH.matcher(event.text());
            if (matcher.find() && DeathCause.WITCH_POISON.name().equals(matcher.group(2))) {
                Player witch = game.playersByRole(RoleType.WITCH).stream().findFirst().orElse(null);
                if (witch != null) {
                    Player target = game.requirePlayer(Integer.parseInt(matcher.group(1)));
                    add(scores, witch.seatNo(), target.role() == RoleType.WEREWOLF ? config.witchPoisonHit() : config.witchPoisonMiss());
                }
            }
        }
        if (event.type() == EventType.SHERIFF_ELECTED && event.publisherSeat() != null) {
            Player sheriff = game.requirePlayer(event.publisherSeat());
            add(scores, sheriff.seatNo(), sheriff.role().camp() == Camp.GOOD ? config.sheriffGood() : config.sheriffWolf());
        }
        if (event.type() == EventType.SYSTEM_NOTE && event.text().contains("降级")) {
            for (Player player : game.players()) {
                if (event.audience().contains(player.seatNo())) {
                    add(scores, player.seatNo(), config.invalidSpeech());
                }
            }
        }
    }

    private Map<Integer, Double> lastScores(Game game) {
        if (game.scoreboards().isEmpty()) {
            return Map.of();
        }
        Map<Integer, Double> result = new HashMap<>();
        for (PlayerScore score : game.scoreboards().get(game.scoreboards().size() - 1).scores()) {
            result.put(score.seat(), score.score());
        }
        return result;
    }

    private void add(Map<Integer, Double> scores, int seat, double delta) {
        scores.merge(seat, delta, Double::sum);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(10.0, value));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatDelta(double delta) {
        return (delta >= 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%.1f", delta);
    }
}
