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
        Map<String, Object> replay = Map.of(
                "matchId", game.matchId(),
                "preset", "STANDARD_9",
                "seed", game.randomSeed(),
                "ruleConfig", game.ruleConfig(),
                "seating", game.players().stream().map(player -> Map.of(
                        "seat", player.seatNo(),
                        "role", player.role().name()
                )).toList(),
                "events", game.eventLog(),
                "winner", winner,
                "totalRounds", game.roundNo(),
                "agentTrace", game.players().stream().map(player -> Map.of(
                        "seat", player.seatNo(),
                        "trace", journal.entries(player.seatNo())
                )).toList()
        );
        objectMapper.writeValue(workspace.replay().toFile(), replay);
    }

    public void writeMeta(Game game, MatchWorkspace workspace, Camp winner) throws IOException {
        Map<String, Object> meta = Map.of(
                "matchId", game.matchId(),
                "seed", game.randomSeed(),
                "preset", "STANDARD_9",
                "winner", winner.name(),
                "totalRounds", game.roundNo(),
                "seating", game.players().stream().map(Player::role).map(Enum::name).toList()
        );
        objectMapper.writeValue(workspace.meta().toFile(), meta);
    }
}
