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
            return new ParsedAction(reasoning, result.action(), result.warning());
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
