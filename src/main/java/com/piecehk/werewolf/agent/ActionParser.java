package com.piecehk.werewolf.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.piecehk.werewolf.agent.action.ActionType;
import com.piecehk.werewolf.agent.action.AgentAction;
import com.piecehk.werewolf.agent.action.BadgeTransferAction;
import com.piecehk.werewolf.agent.action.HunterShootAction;
import com.piecehk.werewolf.agent.action.NoOpAction;
import com.piecehk.werewolf.agent.action.SeerCheckAction;
import com.piecehk.werewolf.agent.action.SheriffRunAction;
import com.piecehk.werewolf.agent.action.SheriffVoteAction;
import com.piecehk.werewolf.agent.action.SpeakAction;
import com.piecehk.werewolf.agent.action.SpeechOrderAction;
import com.piecehk.werewolf.agent.action.VoteAction;
import com.piecehk.werewolf.agent.action.WitchAction;
import com.piecehk.werewolf.agent.action.WolfKillAction;
import com.piecehk.werewolf.core.model.SpeechOrder;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionParser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParsedAction parse(String raw, ActionType fallbackType, Set<Integer> validTargets, int maxSpeechChars) {
        try {
            String json = extractJson(raw);
            JsonNode root = objectMapper.readTree(json);
            String reasoning = root.path("reasoning").asText("");
            JsonNode actionNode = root.path("action");
            ActionType type = parseType(actionNode.path("type").asText(fallbackType.name()), fallbackType);
            ParseResult result = toAction(type, actionNode, validTargets, maxSpeechChars);
            AgentAction action = result.action();
            String warning = result.warning();
            // 真实 LLM 常常把目标只写进 reasoning（如“刀4号”“查1号”），却漏填 targetSeat。
            // 这里在目标为空时从思考文本中按“动作动词+座位号”回填，避免误判为空刀/弃验。
            if (canSalvageTarget(type) && nullTarget(action)) {
                Integer recovered = recoverTargetFromReasoning(reasoning, type, validTargets);
                if (recovered != null) {
                    action = withTarget(type, recovered);
                    warning = "目标字段缺失，已据思考回填座位" + recovered;
                }
            }
            return new ParsedAction(reasoning, action, warning);
        } catch (Exception e) {
            return new ParsedAction("", fallbackAction(fallbackType), "解析失败，已降级为安全动作");
        }
    }

    private ParseResult toAction(ActionType type, JsonNode actionNode, Set<Integer> validTargets, int maxSpeechChars) {
        String warning = null;
        AgentAction action = switch (type) {
            case SPEAK -> {
                String speech = actionNode.path("speech").asText("");
                String truncated = truncate(speech, maxSpeechChars);
                if (!speech.equals(truncated)) {
                    warning = "发言超长已截断";
                }
                yield new SpeakAction(truncated, actionNode.path("withdraw").asBoolean(false));
            }
            case VOTE -> new VoteAction(validTarget(actionNode.get("targetSeat"), validTargets));
            case WOLF_KILL -> new WolfKillAction(validTarget(actionNode.get("targetSeat"), validTargets));
            case SEER_CHECK -> new SeerCheckAction(validTarget(actionNode.get("targetSeat"), validTargets));
            case WITCH -> new WitchAction(actionNode.path("useAntidote").asBoolean(false),
                    validTarget(actionNode.get("poisonSeat"), validTargets));
            case HUNTER_SHOOT -> new HunterShootAction(validTarget(actionNode.get("targetSeat"), validTargets));
            case SHERIFF_RUN -> new SheriffRunAction(actionNode.path("run").asBoolean(false));
            case SHERIFF_VOTE -> new SheriffVoteAction(validTarget(actionNode.get("targetSeat"), validTargets));
            case SPEECH_ORDER -> new SpeechOrderAction(parseSpeechOrder(actionNode.path("order").asText(""), SpeechOrder.SHERIFF_LEFT));
            case BADGE_TRANSFER -> new BadgeTransferAction(validTarget(actionNode.get("targetSeat"), validTargets));
            case NOOP -> new NoOpAction();
        };
        if (requiresTarget(type) && targetInvalid(action)) {
            warning = merge(warning, "非法目标已降级");
        }
        return new ParseResult(action, warning);
    }

    private boolean requiresTarget(ActionType type) {
        return type == ActionType.WOLF_KILL || type == ActionType.SEER_CHECK || type == ActionType.HUNTER_SHOOT;
    }

    private boolean canSalvageTarget(ActionType type) {
        return type == ActionType.WOLF_KILL || type == ActionType.SEER_CHECK
                || type == ActionType.HUNTER_SHOOT || type == ActionType.VOTE;
    }

    private boolean nullTarget(AgentAction action) {
        if (action instanceof WolfKillAction a) return a.targetSeat() == null;
        if (action instanceof SeerCheckAction a) return a.targetSeat() == null;
        if (action instanceof HunterShootAction a) return a.targetSeat() == null;
        if (action instanceof VoteAction a) return a.targetSeat() == null;
        return false;
    }

    private AgentAction withTarget(ActionType type, int seat) {
        return switch (type) {
            case WOLF_KILL -> new WolfKillAction(seat);
            case SEER_CHECK -> new SeerCheckAction(seat);
            case HUNTER_SHOOT -> new HunterShootAction(seat);
            case VOTE -> new VoteAction(seat);
            default -> new NoOpAction();
        };
    }

    // 仅在“动作动词紧跟座位号”时回填，避免把 reasoning 里的“避免动1、2、5、7位”等无关座位误当成目标。
    private Integer recoverTargetFromReasoning(String reasoning, ActionType type, Set<Integer> validTargets) {
        if (reasoning == null || reasoning.isBlank() || validTargets == null || validTargets.isEmpty()) {
            return null;
        }
        String verbs = switch (type) {
            case WOLF_KILL -> "刀掉|刀杀|出刀|击杀|杀掉|宰|刀|砍|杀|带走|选|锁定|针对|目标定?在?";
            case SEER_CHECK -> "查验|验人|查|验|看一?下?|选|锁定|针对|目标定?在?";
            case HUNTER_SHOOT -> "开枪带走|带走|射杀|开枪|射|枪|杀|选|锁定|针对|目标定?在?";
            case VOTE -> "投票给|改投|投给|投|放逐|票|选|锁定|针对|目标定?在?";
            default -> null;
        };
        if (verbs == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("(?:" + verbs + ")\\s*第?\\s*([1-9]|[一二三四五六七八九])\\s*号?");
        Matcher matcher = pattern.matcher(reasoning);
        while (matcher.find()) {
            Integer seat = toSeat(matcher.group(1));
            if (seat != null && validTargets.contains(seat)) {
                return seat;
            }
        }
        return null;
    }

    private Integer toSeat(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        char c = token.charAt(0);
        if (c >= '1' && c <= '9') {
            return c - '0';
        }
        int idx = "一二三四五六七八九".indexOf(c);
        return idx >= 0 ? idx + 1 : null;
    }

    private boolean targetInvalid(AgentAction action) {
        if (action instanceof WolfKillAction a) return a.targetSeat() == null;
        if (action instanceof SeerCheckAction a) return a.targetSeat() == null;
        if (action instanceof HunterShootAction a) return a.targetSeat() == null;
        return false;
    }

    private Integer validTarget(JsonNode node, Set<Integer> validTargets) {
        if (node == null || node.isNull() || !node.canConvertToInt()) {
            return null;
        }
        int target = node.asInt();
        return validTargets.contains(target) ? target : null;
    }

    private AgentAction fallbackAction(ActionType type) {
        return switch (type) {
            case SPEAK -> new SpeakAction("");
            case VOTE -> new VoteAction(null);
            case WOLF_KILL -> new WolfKillAction(null);
            case SEER_CHECK -> new SeerCheckAction(null);
            case WITCH -> new WitchAction(false, null);
            case SHERIFF_RUN -> new SheriffRunAction(false);
            case SHERIFF_VOTE -> new SheriffVoteAction(null);
            case SPEECH_ORDER -> new SpeechOrderAction(SpeechOrder.SHERIFF_LEFT);
            case BADGE_TRANSFER -> new BadgeTransferAction(null);
            case HUNTER_SHOOT, NOOP -> new NoOpAction();
        };
    }

    private ActionType parseType(String value, ActionType fallback) {
        try {
            return ActionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private SpeechOrder parseSpeechOrder(String value, SpeechOrder fallback) {
        try {
            return SpeechOrder.valueOf(value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private String extractJson(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        cleaned = cleaned.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("no json object");
        }
        return cleaned.substring(start, end + 1);
    }

    private String truncate(String value, int maxSpeechChars) {
        int[] cps = value.codePoints().toArray();
        if (cps.length <= maxSpeechChars) {
            return value;
        }
        return new String(cps, 0, maxSpeechChars);
    }

    private String merge(String first, String second) {
        return first == null ? second : first + "；" + second;
    }

    private record ParseResult(AgentAction action, String warning) {
    }
}
