package com.piecehk.werewolf.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class GameFactory {
    private GameFactory() {
    }

    public static Game standardNine(String matchId, long seed, RuleConfig ruleConfig) {
        List<RoleType> roles = new ArrayList<>(List.of(
                RoleType.WEREWOLF, RoleType.WEREWOLF, RoleType.WEREWOLF,
                RoleType.VILLAGER, RoleType.VILLAGER, RoleType.VILLAGER,
                RoleType.SEER, RoleType.WITCH, RoleType.HUNTER
        ));
        Collections.shuffle(roles, new java.util.Random(seed));
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            int seat = i + 1;
            players.add(new Player(seat, "Player-" + seat, roles.get(i), "agent-" + seat, false));
        }
        return new Game(matchId == null ? "match-" + UUID.randomUUID() : matchId, players, ruleConfig, seed);
    }

    public static Game fixed(String matchId, RuleConfig ruleConfig, RoleType... roles) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < roles.length; i++) {
            int seat = i + 1;
            players.add(new Player(seat, "Player-" + seat, roles[i], "agent-" + seat, false));
        }
        return new Game(matchId, players, ruleConfig, 1L);
    }
}
