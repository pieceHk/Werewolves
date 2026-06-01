package com.piecehk.werewolf.agent.llm;

import java.time.Duration;
import java.util.Map;

public record ChatOptions(
        String model,
        double temperature,
        int maxTokens,
        Duration timeout,
        boolean jsonMode,
        Map<String, Object> extra
) {
    public static ChatOptions defaults(String model) {
        return new ChatOptions(model, 0.8, 512, Duration.ofSeconds(60), true, Map.of());
    }

    public ChatOptions withTimeout(Duration timeout) {
        return new ChatOptions(model, temperature, maxTokens, timeout, jsonMode, extra);
    }
}
