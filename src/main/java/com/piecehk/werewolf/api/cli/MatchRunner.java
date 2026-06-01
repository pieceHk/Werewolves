package com.piecehk.werewolf.api.cli;

import com.piecehk.werewolf.agent.llm.LLMClient;
import com.piecehk.werewolf.agent.llm.MockLLMClient;
import com.piecehk.werewolf.agent.llm.QwenLLMClient;
import com.piecehk.werewolf.game.MatchManager;
import com.piecehk.werewolf.game.MatchResult;
import com.piecehk.werewolf.infra.output.AgentJournalWriter;
import com.piecehk.werewolf.infra.output.GodViewLogWriter;
import com.piecehk.werewolf.infra.output.ReplayWriter;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class MatchRunner {
    private MatchRunner() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        long seed = Long.parseLong(options.getOrDefault("seed", "12345"));
        int roundsCap = Integer.parseInt(options.getOrDefault("rounds-cap", "20"));
        Path out = Path.of(options.getOrDefault("out", "./matches"));
        String llm = options.getOrDefault("llm", "mock");

        LLMClient client = "qwen".equalsIgnoreCase(llm)
                ? QwenLLMClient.fromEnvironment(
                options.getOrDefault("qwen-base-url", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                Duration.ofSeconds(10), 3, 6)
                : MockLLMClient.deterministic();

        MatchResult result = new MatchManager().runSingleMatch(seed, roundsCap, out, client);
        new GodViewLogWriter().write(result.game(), result.workspace());
        new AgentJournalWriter().write(result.game(), result.journal(), result.workspace());
        ReplayWriter replayWriter = new ReplayWriter();
        replayWriter.write(result.game(), result.journal(), result.workspace(), result.winner());
        replayWriter.writeMeta(result.game(), result.workspace(), result.winner());

        System.out.println("matchId=" + result.game().matchId());
        System.out.println("winner=" + result.winner());
        System.out.println("output=" + result.workspace().root().toAbsolutePath());
        result.game().eventLog().stream()
                .filter(event -> event.visibility().name().equals("PUBLIC"))
                .forEach(event -> System.out.println(event.title() + " | " + event.text()));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
                options.put(key, value);
            }
        }
        return options;
    }
}
