package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.DeathCause;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GameFactory;
import com.piecehk.werewolf.core.model.NightActions;
import com.piecehk.werewolf.core.model.RoleType;
import com.piecehk.werewolf.core.model.RuleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NightResolverTest {
    private final NightResolver resolver = new NightResolver();

    @Test
    void poisonKillsEvenWhenWolfTargetIsSaved() {
        RuleConfig config = new RuleConfig(
                RuleConfig.defaults().winCondition(),
                RuleConfig.defaults().witchSelfSave(),
                true,
                RuleConfig.defaults().hunterShootWhenPoisoned(),
                RuleConfig.defaults().hunterShootWhenVoted(),
                RuleConfig.defaults().allowFirstNightLastWords(),
                RuleConfig.defaults().voteTie(),
                RuleConfig.defaults().maxSpeechChars()
        );
        Game game = GameFactory.fixed("night-test", config,
                RoleType.WEREWOLF, RoleType.WEREWOLF, RoleType.WEREWOLF,
                RoleType.VILLAGER, RoleType.SEER, RoleType.VILLAGER,
                RoleType.HUNTER, RoleType.WITCH, RoleType.VILLAGER);

        NightOutcome outcome = resolver.resolve(game, new NightActions(4, 5, true, 6));

        assertThat(outcome.deaths()).containsEntry(6, DeathCause.WITCH_POISON);
        assertThat(outcome.deaths()).doesNotContainKey(4);
    }

    @Test
    void rejectsBothPotionsByDefault() {
        Game game = sampleGame();

        assertThatThrownBy(() -> resolver.resolve(game, new NightActions(4, 5, true, 6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both potions");
    }

    @Test
    void firstNightSelfSaveIsAllowedButSecondNightIsRejected() {
        Game firstNight = sampleGame();
        assertThat(resolver.resolve(firstNight, new NightActions(8, 5, true, null)).deaths()).isEmpty();

        Game secondNight = sampleGame();
        secondNight.nextRound();
        assertThatThrownBy(() -> resolver.resolve(secondNight, new NightActions(8, 5, true, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("self save");
    }

    private Game sampleGame() {
        RuleConfig config = RuleConfig.defaults();
        return GameFactory.fixed("night-test", config,
                RoleType.WEREWOLF, RoleType.WEREWOLF, RoleType.WEREWOLF,
                RoleType.VILLAGER, RoleType.SEER, RoleType.VILLAGER,
                RoleType.HUNTER, RoleType.WITCH, RoleType.VILLAGER);
    }
}
