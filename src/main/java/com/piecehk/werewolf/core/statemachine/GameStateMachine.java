package com.piecehk.werewolf.core.statemachine;

import com.piecehk.werewolf.core.event.BasicGameEvent;
import com.piecehk.werewolf.core.event.EventType;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GamePhase;

public final class GameStateMachine {
    public void transition(Game game, GamePhase target) {
        GamePhase current = game.phase();
        if (!isLegal(current, target)) {
            throw new IllegalPhaseTransitionException("illegal phase transition: " + current + " -> " + target);
        }
        game.setPhase(target);
        game.addEvent(BasicGameEvent.publicEvent(
                EventType.PHASE_CHANGED,
                game.roundNo(),
                target,
                null,
                null,
                "阶段切换",
                current + " -> " + target
        ));
    }

    private boolean isLegal(GamePhase current, GamePhase target) {
        if (target == GamePhase.GAME_OVER) {
            return true;
        }
        return switch (current) {
            case PREPARING -> target == GamePhase.NIGHT;
            case NIGHT -> target == GamePhase.DAY_ANNOUNCE;
            case DAY_ANNOUNCE -> target == GamePhase.DAY_DISCUSS;
            case DAY_DISCUSS -> target == GamePhase.DAY_VOTE;
            case DAY_VOTE -> target == GamePhase.NIGHT;
            case GAME_OVER -> false;
        };
    }
}
