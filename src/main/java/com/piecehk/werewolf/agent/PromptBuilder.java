package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.agent.action.ActionType;
import com.piecehk.werewolf.agent.llm.ChatMessage;
import com.piecehk.werewolf.core.event.GameEvent;
import com.piecehk.werewolf.core.model.RoleType;

import java.util.List;
import java.util.stream.Collectors;

public final class PromptBuilder {
    public List<ChatMessage> build(AgentContext context, ActionType requiredAction, int maxSpeechChars) {
        return List.of(ChatMessage.system(systemPrompt(context, requiredAction, maxSpeechChars)),
                ChatMessage.user(userPrompt(context, requiredAction, maxSpeechChars)));
    }

    public String roleSystemPrompt(AgentContext context, int maxSpeechChars) {
        return "你正在参与一场 9 人标准狼人杀（预女猎）。你的座位是 "
                + context.self().seatNo() + "，身份是【" + context.role().displayName() + "】。"
                + "你只能依据自己可见的信息推理，不得编造无法看到的内容。"
                + "所有发言不超过 " + maxSpeechChars + " 个汉字。仅返回 JSON。";
    }

    private String systemPrompt(AgentContext context, ActionType requiredAction, int maxSpeechChars) {
        return roleSystemPrompt(context, maxSpeechChars)
                + "\n当前要求动作：" + requiredAction
                + "\nJSON 格式：{\"reasoning\":\"仅写入私人记忆\",\"action\":{\"type\":\"" + requiredAction + "\"}}";
    }

    private String userPrompt(AgentContext context, ActionType requiredAction, int maxSpeechChars) {
        String visibleEvents = context.visibleEvents().stream()
                .map(GameEvent::text)
                .collect(Collectors.joining("\n"));
        String rolePrivate = switch (context.role()) {
            case WEREWOLF -> "狼队友座位：" + context.wolfTeammates();
            case WITCH -> "今晚被刀：" + context.wolfVictim() + "；解药：" + context.antidoteAvailable() + "；毒药：" + context.poisonAvailable();
            case SEER -> "你是预言家，只能看到自己的查验事件。";
            case HUNTER -> "你是猎人，出局且规则允许时可开枪。";
            case VILLAGER -> "你是村民，无夜间技能。";
        };
        String speechRule = "如需发言，speech 必须紧扣判断/意图，不超过 " + maxSpeechChars + " 个汉字，禁止冗长铺陈。";
        return """
                当前第 %d 轮，阶段 %s。
                存活玩家：%s。
                私有信息：%s
                你的私人笔记：%s
                可见事件：
                %s
                %s
                请输出 required action=%s 的严格 JSON，不要 Markdown 代码块。
                """.formatted(context.roundNo(), context.phase(), context.aliveSeats(), rolePrivate,
                context.journalNotes(), visibleEvents, speechRule, requiredAction);
    }
}
