package com.piecehk.werewolf.infra.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record MatchWorkspace(
        Path root,
        Path godView,
        Path agentsDir,
        Path humanView,
        Path replay,
        Path meta
) {
    public static MatchWorkspace create(Path outputDir, String matchId) throws IOException {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        String shortId = matchId.length() > 8 ? matchId.substring(matchId.length() - 8) : matchId;
        Path root = outputDir.resolve("match-" + timestamp + "-" + shortId);
        Path agents = root.resolve("agents");
        Files.createDirectories(agents);
        Path humanView = root.resolve("human-view.md");
        Files.writeString(humanView, "# human-view\n\nv1 预留，当前无真人玩家。\n");
        return new MatchWorkspace(root, root.resolve("god-view.md"), agents,
                humanView, root.resolve("replay.json"), root.resolve("meta.json"));
    }
}
