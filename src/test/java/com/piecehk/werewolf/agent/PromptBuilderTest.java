package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.agent.action.ActionType;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GameFactory;
import com.piecehk.werewolf.core.model.RoleType;
import com.piecehk.werewolf.core.model.RuleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {
    @Test
    void witchPromptContainsHardRules() {
        Game game = GameFactory.fixed("prompt-test", RuleConfig.defaults(),
                RoleType.WEREWOLF, RoleType.WEREWOLF, RoleType.WEREWOLF,
                RoleType.VILLAGER, RoleType.SEER, RoleType.VILLAGER,
                RoleType.HUNTER, RoleType.WITCH, RoleType.VILLAGER);
        AgentContext context = new ContextBuilder().build(game, game.requirePlayer(8), 8, "");

        String system = new PromptBuilder().build(context, ActionType.WITCH, 100).get(0).content();

        assertThat(system).contains("不能用解药救自己");
        assertThat(system).contains("不能同一晚同时用解药和毒药");
        assertThat(system).contains("解药只能救“当晚被狼人击杀者”");
    }

    @Test
    void everyRolePromptStartsWithRuleBriefing() {
        Game game = GameFactory.fixed("prompt-test", RuleConfig.defaults(),
                RoleType.WEREWOLF, RoleType.VILLAGER, RoleType.SEER,
                RoleType.WITCH, RoleType.HUNTER, RoleType.VILLAGER,
                RoleType.VILLAGER, RoleType.WEREWOLF, RoleType.WEREWOLF);
        PromptBuilder builder = new PromptBuilder();
        ContextBuilder contextBuilder = new ContextBuilder();

        for (int seat = 1; seat <= 5; seat++) {
            String system = builder.roleSystemPrompt(contextBuilder.build(game, game.requirePlayer(seat), null, ""), 100);
            assertThat(system).contains("这是一场9人“预女猎”标准狼人杀");
            assertThat(system).contains("所有回答只输出约定 JSON");
        }
    }
}
