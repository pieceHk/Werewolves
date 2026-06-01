package com.piecehk.werewolf.infra.output;

import com.piecehk.werewolf.agent.AgentContext;
import com.piecehk.werewolf.agent.ContextBuilder;
import com.piecehk.werewolf.agent.PromptBuilder;
import com.piecehk.werewolf.core.event.GameEvent;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

public final class GodViewLogWriter {
    public void write(Game game, MatchWorkspace workspace) throws IOException {
        PromptBuilder promptBuilder = new PromptBuilder();
        ContextBuilder contextBuilder = new ContextBuilder();
        StringBuilder sb = new StringBuilder();
        sb.append("# 对局 god-view 日志 ").append(game.matchId()).append("\n\n");
        sb.append("seed=").append(game.randomSeed()).append(" · preset=STANDARD_9\n\n");
        sb.append("座位角色（上帝视角）：");
        sb.append(game.players().stream()
                .map(player -> player.seatNo() + "=" + player.role().displayName())
                .collect(Collectors.joining(" ")));
        sb.append("\n\n## 各角色 System Prompt\n\n");
        for (Player player : game.players()) {
            AgentContext context = contextBuilder.build(game, player, null, "");
            sb.append("### 座位").append(player.seatNo()).append("（").append(player.role().displayName()).append("）\n");
            sb.append(promptBuilder.roleSystemPrompt(context, game.ruleConfig().maxSpeechChars())).append("\n\n");
        }
        for (GameEvent event : game.eventLog()) {
            sb.append("────────────────────────────────────────────\n");
            sb.append("【第 ").append(event.roundNo()).append(" 轮 · ").append(event.phase()).append(" · ")
                    .append(event.title()).append("】 [").append(event.visibility());
            if (!event.audience().isEmpty()) {
                sb.append(" · 仅").append(event.audience()).append("可见");
            }
            sb.append("]  发布：");
            if (event.publisherSeat() == null) {
                sb.append("系统");
            } else {
                sb.append("座位").append(event.publisherSeat()).append("(")
                        .append(event.publisherRole().displayName()).append(")");
            }
            sb.append("\n────────────────────────────────────────────\n");
            sb.append(event.text()).append("\n\n");
        }
        Files.writeString(workspace.godView(), sb.toString());
    }
}
