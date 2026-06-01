package com.piecehk.werewolf.agent.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MockLLMClient implements LLMClient {
    private static final Pattern REQUIRED_ACTION = Pattern.compile("当前要求动作：([A-Z_]+)");
    private static final Pattern SELF_SEAT = Pattern.compile("你的座位是 (\\d+)");
    private static final Pattern ALIVE_SEATS = Pattern.compile("存活玩家：\\[([^]]*)]");
    private static final Pattern WOLF_TEAMMATES = Pattern.compile("狼队友座位：\\[([^]]*)]");

    private final Queue<String> scripted = new ArrayDeque<>();
    private final AtomicInteger counter = new AtomicInteger();

    public MockLLMClient(List<String> scripted) {
        this.scripted.addAll(scripted);
    }

    public static MockLLMClient deterministic() {
        return new MockLLMClient(List.of());
    }

    @Override
    public synchronized String chat(List<ChatMessage> messages, ChatOptions options) {
        if (!scripted.isEmpty()) {
            return scripted.poll();
        }
        String joined = messages.stream().map(ChatMessage::content).reduce("", (a, b) -> a + "\n" + b);
        int id = counter.incrementAndGet();
        String requiredAction = requiredAction(joined);
        int selfSeat = firstInt(SELF_SEAT, joined, 0);
        List<Integer> aliveSeats = seats(ALIVE_SEATS, joined);
        List<Integer> wolfTeammates = seats(WOLF_TEAMMATES, joined);

        if ("WOLF_KILL".equals(requiredAction)) {
            int target = firstTarget(aliveSeats, selfSeat, wolfTeammates);
            return json("选择最靠前的非狼存活目标。", "{\"type\":\"WOLF_KILL\",\"targetSeat\":" + target + "}");
        }
        if ("SEER_CHECK".equals(requiredAction)) {
            int target = firstTarget(aliveSeats, selfSeat, List.of());
            return json("先查验低号可疑玩家。", "{\"type\":\"SEER_CHECK\",\"targetSeat\":" + target + "}");
        }
        if ("WITCH".equals(requiredAction)) {
            return json("默认保守用药。", "{\"type\":\"WITCH\",\"useAntidote\":false,\"poisonSeat\":null}");
        }
        if ("VOTE".equals(requiredAction)) {
            int target = rotatingTarget(aliveSeats, selfSeat, id);
            return json("根据公开信息投出一票。", "{\"type\":\"VOTE\",\"targetSeat\":" + target + "}");
        }
        if ("HUNTER_SHOOT".equals(requiredAction)) {
            return json("不开枪以免误伤。", "{\"type\":\"NOOP\"}");
        }
        return json("保持身份弹性，观察发言。", "{\"type\":\"SPEAK\",\"speech\":\"我是好人，先听大家发言，再根据投票和站边判断。\"}");
    }

    private String json(String reasoning, String action) {
        return "{\"reasoning\":\"" + reasoning + "\",\"action\":" + action + "}";
    }

    private String requiredAction(String prompt) {
        Matcher matcher = REQUIRED_ACTION.matcher(prompt);
        return matcher.find() ? matcher.group(1) : "SPEAK";
    }

    private int firstInt(Pattern pattern, String value, int fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private List<Integer> seats(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return List.of();
        }
        String raw = matcher.group(1).trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        List<Integer> seats = new ArrayList<>();
        for (String item : raw.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                seats.add(Integer.parseInt(trimmed));
            }
        }
        return seats;
    }

    private int firstTarget(List<Integer> aliveSeats, int selfSeat, List<Integer> excluded) {
        return aliveSeats.stream()
                .filter(seat -> seat != selfSeat)
                .filter(seat -> !excluded.contains(seat))
                .findFirst()
                .orElse(0);
    }

    private int rotatingTarget(List<Integer> aliveSeats, int selfSeat, int offset) {
        List<Integer> targets = aliveSeats.stream().filter(seat -> seat != selfSeat).toList();
        if (targets.isEmpty()) {
            return 0;
        }
        return targets.get(offset % targets.size());
    }
}
