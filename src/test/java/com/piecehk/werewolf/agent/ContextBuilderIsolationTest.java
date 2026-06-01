package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.core.event.BasicGameEvent;
import com.piecehk.werewolf.core.event.EventType;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GameFactory;
import com.piecehk.werewolf.core.model.GamePhase;
import com.piecehk.werewolf.core.model.RoleType;
import com.piecehk.werewolf.core.model.RuleConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderIsolationTest {
    private final ContextBuilder contextBuilder = new ContextBuilder();

    @Test
    void villagerCannotSeeWolfPrivateDiscussionOrSeerCheck() {
        Game game = sampleGame();
        game.addEvent(BasicGameEvent.privateEvent(EventType.WOLF_DISCUSSION, Set.of(1, 2, 3), 1,
                GamePhase.NIGHT, 1, RoleType.WEREWOLF, "狼人商讨", "狼队友决定刀4"));
        game.addEvent(BasicGameEvent.privateEvent(EventType.SEER_CHECKED, Set.of(7), 1,
                GamePhase.NIGHT, 7, RoleType.SEER, "查验", "查验1为狼人"));
        game.addEvent(BasicGameEvent.publicEvent(EventType.PLAYER_SPOKE, 1,
                GamePhase.DAY_DISCUSS, 4, RoleType.VILLAGER, "发言", "公开发言"));

        AgentContext villager = contextBuilder.build(game, game.requirePlayer(4), null, "");

        assertThat(villager.visibleEvents()).extracting(event -> event.text())
                .containsExactly("公开发言");
        assertThat(villager.wolfTeammates()).isEmpty();
    }

    @Test
    void wolfCanSeeTeammatesAndWolfDiscussion() {
        Game game = sampleGame();
        game.addEvent(BasicGameEvent.privateEvent(EventType.WOLF_DISCUSSION, Set.of(1, 2, 3), 1,
                GamePhase.NIGHT, 1, RoleType.WEREWOLF, "狼人商讨", "狼队友决定刀4"));

        AgentContext wolf = contextBuilder.build(game, game.requirePlayer(1), null, "");

        assertThat(wolf.wolfTeammates()).containsExactly(2, 3);
        assertThat(wolf.visibleEvents()).extracting(event -> event.text()).contains("狼队友决定刀4");
    }

    private Game sampleGame() {
        return GameFactory.fixed("ctx-test", RuleConfig.defaults(),
                RoleType.WEREWOLF, RoleType.WEREWOLF, RoleType.WEREWOLF,
                RoleType.VILLAGER, RoleType.VILLAGER, RoleType.VILLAGER,
                RoleType.SEER, RoleType.WITCH, RoleType.HUNTER);
    }
}
