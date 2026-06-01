package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.agent.action.ActionType;
import com.piecehk.werewolf.agent.llm.ChatMessage;
import com.piecehk.werewolf.core.event.GameEvent;
import com.piecehk.werewolf.core.model.RoleType;

import java.util.List;
import java.util.stream.Collectors;

public final class PromptBuilder {
    private static final String COMMON_RULES_BRIEF = """
            这是一场9人“预女猎”标准狼人杀：3狼人、3平民、1预言家、1女巫、1猎人。
            阵营与胜利：狼人阵营 vs 好人阵营（预言家/女巫/猎人/平民）。屠边即狼胜；狼人全部出局则好人胜。
            流程：夜晚（狼人刀人、预言家验人、女巫用药）→ 白天（第1天先竞选警长、公布死讯、发言、投票放逐）。
            发言要求：任何发言都必须紧扣你的判断或意图，且不超过%s个汉字。
            你只能依据你已知的信息推理，不得编造你看不到的内容。所有回答只输出约定 JSON，不要包含多余文字或代码块。
            """;

    public List<ChatMessage> build(AgentContext context, ActionType requiredAction, int maxSpeechChars) {
        return List.of(ChatMessage.system(systemPrompt(context, requiredAction, maxSpeechChars)),
                ChatMessage.user(userPrompt(context, requiredAction, maxSpeechChars)));
    }

    public String roleSystemPrompt(AgentContext context, int maxSpeechChars) {
        return "你的座位是 " + context.self().seatNo() + "，身份是【" + context.role().displayName() + "】。\n"
                + COMMON_RULES_BRIEF.formatted(maxSpeechChars)
                + roleRulesBrief(context.role())
                + witchRuntimeBrief(context);
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
        String taskRule = taskRule(requiredAction);
        return """
                当前第 %d 轮，阶段 %s。
                存活玩家：%s。
                私有信息：%s
                你的私人笔记：%s
                可见事件：
                %s
                %s
                %s
                请输出 required action=%s 的严格 JSON，不要 Markdown 代码块。
                """.formatted(context.roundNo(), context.phase(), context.aliveSeats(), rolePrivate,
                context.journalNotes(), visibleEvents, speechRule, taskRule, requiredAction);
    }

    private String roleRulesBrief(RoleType role) {
        return switch (role) {
            case WEREWOLF -> """
                    你是【狼人】。你与狼队友互相知道身份，每晚共同选择击杀一名玩家（可空刀）。
                    你的目标是隐藏身份、误导好人，直到屠边获胜。白天你要伪装成好人发言与投票。
                    夜晚商讨仅狼人可见；你不知道任何好人的真实身份。
                    """;
            case VILLAGER -> """
                    你是【平民】，没有任何夜间技能。你的唯一手段是白天的发言与投票。
                    你的目标是与其他好人一起找出并放逐狼人。你不知道任何人的真实身份，只能靠推理。
                    """;
            case SEER -> """
                    你是【预言家】，好人阵营核心。每晚可查验一名玩家，得知其为“好人”或“狼人”（每晚仅一次、仅一人，可选择不验）。
                    你的目标是利用查验信息引导好人放逐狼人。白天你可以选择是否公开身份与验人结果。
                    """;
            case WITCH -> """
                    你是【女巫】，好人阵营。你有解药×1、毒药×1，整局各仅一次。
                    硬规则：①不能用解药救自己；②不能同一晚同时用解药和毒药；③解药只能救“当晚被狼人击杀者”，毒药无视解药。
                    每晚你会被告知今晚被刀者，然后决定是否用药。不要尝试被禁止的操作。
                    """;
            case HUNTER -> """
                    你是【猎人】，好人阵营。当你出局时（被狼人击杀或被投票放逐），你可以开枪带走一名存活玩家。
                    注意：若你是被女巫毒药毒死的，则不能开枪。你白天没有特殊技能，靠发言与投票，并隐藏好开枪威慑。
                    """;
        };
    }

    private String witchRuntimeBrief(AgentContext context) {
        if (context.role() != RoleType.WITCH) {
            return "";
        }
        return "今晚被狼人袭击的是：" + context.wolfVictim()
                + "（null 表示今晚无人被刀）。你当前剩余：解药 " + context.antidoteAvailable()
                + "，毒药 " + context.poisonAvailable()
                + "。若 useAntidote 为 true，则 poisonSeat 必须为 null；你也不能对自己使用解药。\n";
    }

    private String taskRule(ActionType requiredAction) {
        return switch (requiredAction) {
            case SHERIFF_RUN -> "现在是第1天警长竞选。action.type 为 SHERIFF_RUN，run 为 true 表示参选，false 表示不上警。";
            case SHERIFF_VOTE -> "你是警下玩家，请投票选出警长。action.type 为 SHERIFF_VOTE，targetSeat 为支持座位，弃票为 null。";
            case SPEECH_ORDER -> "你已当选警长，请指定发言顺序。action.type 为 SPEECH_ORDER，order 取 SHERIFF_LEFT/SHERIFF_RIGHT/DEAD_LEFT/DEAD_RIGHT。";
            case BADGE_TRANSFER -> "你是出局警长，请处理警徽。action.type 为 BADGE_TRANSFER，targetSeat 为移交对象，撕毁为 null。";
            default -> "";
        };
    }
}
