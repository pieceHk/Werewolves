package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.core.event.GameEvent;
import com.piecehk.werewolf.core.model.GamePhase;
import com.piecehk.werewolf.core.model.Player;
import com.piecehk.werewolf.core.model.RoleType;

import java.util.List;

public record AgentContext(
        String matchId,
        int roundNo,
        GamePhase phase,
        Player self,
        List<Integer> aliveSeats,
        List<GameEvent> visibleEvents,
        List<Integer> wolfTeammates,
        Integer wolfVictim,
        boolean antidoteAvailable,
        boolean poisonAvailable,
        String journalNotes
) {
    public RoleType role() {
        return self.role();
    }
}
