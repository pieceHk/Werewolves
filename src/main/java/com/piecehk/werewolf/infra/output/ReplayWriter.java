package com.piecehk.werewolf.infra.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.piecehk.werewolf.agent.AgentJournal;
import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.Player;

import java.io.IOException;
import java.util.Map;

public final class ReplayWriter {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(Game game, AgentJournal journal, MatchWorkspace workspace, Camp winner) throws IOException {
        Map<String, Object> replay = new java.util.LinkedHashMap<>();
        replay.put("matchId", game.matchId());
        replay.put("preset", "STANDARD_9");
        replay.put("seed", game.randomSeed());
        replay.put("ruleConfig", game.ruleConfig());
        replay.put("seating", game.players().stream().map(player -> Map.of(
                "seat", player.seatNo(),
                "role", player.role().name()
        )).toList());
        replay.put("events", game.eventLog());
        replay.put("winner", winner == null ? null : winner.name());
        replay.put("totalRounds", game.roundNo());
        replay.put("agentTrace", game.players().stream().map(player -> Map.of(
                "seat", player.seatNo(),
                "trace", journal.entries(player.seatNo())
        )).toList());
        objectMapper.writeValue(workspace.replay().toFile(), replay);
    }

    public void writeMeta(Game game, MatchWorkspace workspace, Camp winner) throws IOException {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("matchId", game.matchId());
        meta.put("seed", game.randomSeed());
        meta.put("preset", "STANDARD_9");
        meta.put("winner", winner == null ? null : winner.name());
        meta.put("phase", game.phase().name());
        meta.put("totalRounds", game.roundNo());
        meta.put("seating", game.players().stream().map(Player::role).map(Enum::name).toList());
        objectMapper.writeValue(workspace.meta().toFile(), meta);
    }
}
