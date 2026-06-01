package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.RoleType;
import com.piecehk.werewolf.core.model.WinCondition;

import java.util.Optional;

public final class WinChecker {
    public Optional<Camp> check(Game game) {
        long aliveWolves = game.alivePlayers().stream().filter(player -> player.role() == RoleType.WEREWOLF).count();
        long aliveGods = game.alivePlayers().stream().filter(player -> player.role().isGod()).count();
        long aliveVillagers = game.alivePlayers().stream().filter(player -> player.role() == RoleType.VILLAGER).count();

        if (aliveWolves == 0) {
            return Optional.of(Camp.GOOD);
        }
        if (game.ruleConfig().winCondition() == WinCondition.KILL_SIDE) {
            if (aliveGods == 0 || aliveVillagers == 0) {
                return Optional.of(Camp.WEREWOLF);
            }
        } else if (aliveGods + aliveVillagers == 0) {
            return Optional.of(Camp.WEREWOLF);
        }
        return Optional.empty();
    }
}
