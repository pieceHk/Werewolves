package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.Player;
import com.piecehk.werewolf.core.model.RoleType;

import java.util.List;

public final class ContextBuilder {
    public AgentContext build(Game game, Player player, Integer wolfVictim, String journalNotes) {
        List<Integer> aliveSeats = game.alivePlayers().stream().map(Player::seatNo).toList();
        List<Integer> wolfTeammates = player.role() == RoleType.WEREWOLF
                ? game.playersByRole(RoleType.WEREWOLF).stream()
                .map(Player::seatNo)
                .filter(seat -> seat != player.seatNo())
                .toList()
                : List.of();
        return new AgentContext(
                game.matchId(),
                game.roundNo(),
                game.phase(),
                player,
                aliveSeats,
                game.eventLog().stream().filter(event -> event.visibleTo(player.seatNo())).toList(),
                wolfTeammates,
                player.role() == RoleType.WITCH ? wolfVictim : null,
                game.witchState().antidoteAvailable(),
                game.witchState().poisonAvailable(),
                journalNotes == null ? "" : journalNotes
        );
    }
}
