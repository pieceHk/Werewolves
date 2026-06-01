package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GameFactory;
import com.piecehk.werewolf.core.model.RoleType;
import com.piecehk.werewolf.core.model.RuleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WinCheckerTest {
    private final WinChecker checker = new WinChecker();

    @Test
    void goodWinsWhenAllWolvesAreDead() {
        Game game = sampleGame();
        game.requirePlayer(1).die();
        game.requirePlayer(2).die();
        game.requirePlayer(3).die();

        assertThat(checker.check(game)).contains(Camp.GOOD);
    }

    @Test
    void wolvesWinByKillingAllVillagersInKillSideMode() {
        Game game = sampleGame();
        game.requirePlayer(4).die();
        game.requirePlayer(5).die();
        game.requirePlayer(6).die();

        assertThat(checker.check(game)).contains(Camp.WEREWOLF);
    }

    private Game sampleGame() {
        return GameFactory.fixed("win-test", RuleConfig.defaults(),
                RoleType.WEREWOLF, RoleType.WEREWOLF, RoleType.WEREWOLF,
                RoleType.VILLAGER, RoleType.VILLAGER, RoleType.VILLAGER,
                RoleType.SEER, RoleType.WITCH, RoleType.HUNTER);
    }
}
