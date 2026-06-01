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
    void nightActionPromptsDocumentTargetSeatField() {
        Game game = GameFactory.fixed("prompt-test", RuleConfig.defaults(),
                RoleType.WEREWOLF, RoleType.WEREWOLF, RoleType.WEREWOLF,
                RoleType.VILLAGER, RoleType.SEER, RoleType.VILLAGER,
                RoleType.HUNTER, RoleType.WITCH, RoleType.VILLAGER);
        PromptBuilder builder = new PromptBuilder();
        ContextBuilder contextBuilder = new ContextBuilder();

        // 狼人首夜击杀：必须告知 targetSeat 字段，且示例 JSON 含 targetSeat
        AgentContext wolf = contextBuilder.build(game, game.requirePlayer(1), null, "");
        String wolfSystem = builder.build(wolf, ActionType.WOLF_KILL, 100).get(0).content();
        String wolfUser = builder.build(wolf, ActionType.WOLF_KILL, 100).get(1).content();
        assertThat(wolfSystem).contains("\"targetSeat\"");
        assertThat(wolfUser).contains("targetSeat");

        // 预言家查验：同样必须给出 targetSeat 字段
        AgentContext seer = contextBuilder.build(game, game.requirePlayer(5), null, "");
        String seerSystem = builder.build(seer, ActionType.SEER_CHECK, 100).get(0).content();
        assertThat(seerSystem).contains("\"targetSeat\"");
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
