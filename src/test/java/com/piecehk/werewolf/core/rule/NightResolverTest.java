package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.DeathCause;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GameFactory;
import com.piecehk.werewolf.core.model.NightActions;
import com.piecehk.werewolf.core.model.RoleType;
import com.piecehk.werewolf.core.model.RuleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NightResolverTest {
    private final NightResolver resolver = new NightResolver();

    @Test
    void poisonKillsNormally() {
        Game game = GameFactory.fixed("night-test", RuleConfig.defaults(),
                RoleType.WEREWOLF, RoleType.WEREWOLF, RoleType.WEREWOLF,
                RoleType.VILLAGER, RoleType.SEER, RoleType.VILLAGER,
                RoleType.HUNTER, RoleType.WITCH, RoleType.VILLAGER);

        NightOutcome outcome = resolver.resolve(game, new NightActions(4, 5, false, 6));

        assertThat(outcome.deaths()).containsEntry(6, DeathCause.WITCH_POISON);
        assertThat(outcome.deaths()).containsEntry(4, DeathCause.WOLF_KILL);
    }

    @Test
    void ignoresPoisonWhenBothPotionsAreSubmitted() {
        Game game = sampleGame();
        NightOutcome outcome = resolver.resolve(game, new NightActions(4, 5, true, 6));

        assertThat(outcome.saved()).isTrue();
        assertThat(outcome.poisonTarget()).isNull();
        assertThat(outcome.deaths()).isEmpty();
    }

    @Test
    void witchCannotSelfSaveEvenOnFirstNight() {
        Game game = sampleGame();
        NightOutcome outcome = resolver.resolve(game, new NightActions(8, 5, true, null));

        assertThat(outcome.saved()).isFalse();
        assertThat(outcome.deaths()).containsEntry(8, DeathCause.WOLF_KILL);
    }

    private Game sampleGame() {
        RuleConfig config = RuleConfig.defaults();
        return GameFactory.fixed("night-test", config,
                RoleType.WEREWOLF, RoleType.WEREWOLF, RoleType.WEREWOLF,
                RoleType.VILLAGER, RoleType.SEER, RoleType.VILLAGER,
                RoleType.HUNTER, RoleType.WITCH, RoleType.VILLAGER);
    }
}
