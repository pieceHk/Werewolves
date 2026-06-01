package com.piecehk.werewolf.infra.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.piecehk.werewolf.agent.AgentJournal;
import com.piecehk.werewolf.agent.llm.ChatMessage;
import com.piecehk.werewolf.agent.llm.ChatOptions;
import com.piecehk.werewolf.agent.llm.LLMClient;
import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.Player;
import com.piecehk.werewolf.core.score.PlayerScore;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameReviewService {
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void review(Game game, AgentJournal journal, MatchWorkspace workspace, Camp winner, LLMClient llmClient) throws IOException {
        String fallbackJson = fallbackJson(game, winner);
        String json = fallbackJson;
        try {
            String prompt = """
                    你是狼人杀资深复盘裁判。以下是一局9人“预女猎”的完整过程、最终得分与玩家私密思考记录。
                    请严格只输出 JSON，字段包含 summary、turningPoints、playerAdvice、mvpSeat、campComment。
                    对局数据：%s
                    最终得分：%s
                    玩家思考记录：%s
                    """.formatted(game.eventLog(), game.scoreboards(), reasonings(game, journal));
            String raw = llmClient.chat(List.of(ChatMessage.system("仅返回 JSON。"), ChatMessage.user(prompt)),
                    ChatOptions.defaults("qwen3-max").withTimeout(Duration.ofSeconds(90)));
            json = extractJson(raw);
            if (!objectMapper.readTree(json).has("summary")) {
                json = fallbackJson;
            }
        } catch (Exception ignored) {
            json = fallbackJson;
        }
        objectMapper.writeValue(workspace.reviewJson().toFile(), objectMapper.readTree(json));
        String markdown = toMarkdown(game, json);
        java.nio.file.Files.writeString(workspace.reviewMd(), markdown);
        java.nio.file.Files.writeString(workspace.godView(),
                java.nio.file.Files.readString(workspace.godView()) + "\n\n" + markdown);
    }

    private String reasonings(Game game, AgentJournal journal) {
        StringBuilder sb = new StringBuilder();
        for (Player player : game.players()) {
            sb.append("座位").append(player.seatNo()).append("（").append(player.role()).append("）\n");
            sb.append(journal.read(player.seatNo())).append("\n\n");
        }
        return sb.toString();
    }

    private String fallbackJson(Game game, Camp winner) throws IOException {
        Integer mvp = game.scoreboards().isEmpty() ? null : game.scoreboards().get(game.scoreboards().size() - 1)
                .scores().stream().findFirst().map(PlayerScore::seat).orElse(null);
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("summary", "对局结束，胜利阵营：" + winner + "。复盘 API 未返回可解析 JSON，已生成规则侧兜底复盘。");
        review.put("turningPoints", List.of());
        review.put("playerAdvice", game.players().stream().map(player -> Map.of(
                        "seat", player.seatNo(),
                        "role", player.role().name(),
                        "tier", "MID",
                        "highlights", "",
                        "issues", "",
                        "improvement", "结合 god-view 与个人 reasoning 复盘发言、投票和技能使用时机。"
                )).toList());
        review.put("mvpSeat", mvp);
        review.put("campComment", Map.of("good", "请复盘投票与神职信息传递。", "wolf", "请复盘伪装、冲票与刀法。"));
        return objectMapper.writeValueAsString(review);
    }

    private String extractJson(String raw) {
        String value = raw == null ? "" : raw.trim().replace("```json", "").replace("```", "").trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("review json missing");
        }
        return value.substring(start, end + 1);
    }

    private String toMarkdown(Game game, String json) {
        return "# 对局全局复盘 · " + game.matchId() + "\n\n```json\n" + json + "\n```\n";
    }
}
