package com.piecehk.werewolf.core.model;

import com.piecehk.werewolf.core.event.GameEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class Game {
    private final String matchId;
    private GamePhase phase;
    private int roundNo;
    private final List<Player> players;
    private final RuleConfig ruleConfig;
    private final WitchState witchState;
    private final List<GameEvent> eventLog = new ArrayList<>();
    private final long randomSeed;
    private final Random random;

    public Game(String matchId, List<Player> players, RuleConfig ruleConfig, long randomSeed) {
        this.matchId = matchId;
        this.phase = GamePhase.PREPARING;
        this.roundNo = 1;
        this.players = new ArrayList<>(players);
        this.players.sort(Comparator.comparingInt(Player::seatNo));
        this.ruleConfig = ruleConfig;
        this.witchState = new WitchState();
        this.randomSeed = randomSeed;
        this.random = new Random(randomSeed);
    }

    public String matchId() {
        return matchId;
    }

    public GamePhase phase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public int roundNo() {
        return roundNo;
    }

    public void nextRound() {
        this.roundNo++;
    }

    public List<Player> players() {
        return List.copyOf(players);
    }

    public List<Player> alivePlayers() {
        return players.stream().filter(Player::isAlive).toList();
    }

    public Optional<Player> playerBySeat(int seatNo) {
        return players.stream().filter(player -> player.seatNo() == seatNo).findFirst();
    }

    public Player requirePlayer(int seatNo) {
        return playerBySeat(seatNo).orElseThrow(() -> new IllegalArgumentException("unknown seat " + seatNo));
    }

    public List<Player> playersByRole(RoleType role) {
        return players.stream().filter(player -> player.role() == role).toList();
    }

    public RuleConfig ruleConfig() {
        return ruleConfig;
    }

    public WitchState witchState() {
        return witchState;
    }

    public List<GameEvent> eventLog() {
        return List.copyOf(eventLog);
    }

    public void addEvent(GameEvent event) {
        eventLog.add(event);
    }

    public long randomSeed() {
        return randomSeed;
    }

    public Random random() {
        return random;
    }
}
