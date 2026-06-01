package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.DeathCause;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.NightActions;
import com.piecehk.werewolf.core.model.RoleType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NightResolver {
    public NightOutcome resolve(Game game, NightActions actions) {
        validateTarget(game, actions.wolfTarget());
        validateTarget(game, actions.seerTarget());
        validateTarget(game, actions.witchPoison());

        boolean witchSave = actions.witchSave();
        Integer witchPoison = actions.witchPoison();
        if (witchSave && actions.wolfTarget() == null) {
            throw new IllegalArgumentException("witch cannot save without wolf target");
        }
        if (witchSave && witchPoison != null && !WitchRules.WITCH_BOTH_POTIONS_SAME_NIGHT) {
            witchPoison = null;
        }
        if (witchSave) {
            witchSave = validateAntidote(game, actions.wolfTarget());
        }
        if (witchPoison != null && !game.witchState().poisonAvailable()) {
            throw new IllegalArgumentException("witch poison is unavailable");
        }

        Map<Integer, DeathCause> deaths = new LinkedHashMap<>();
        if (actions.wolfTarget() != null && !witchSave) {
            deaths.put(actions.wolfTarget(), DeathCause.WOLF_KILL);
        }
        if (witchPoison != null) {
            deaths.put(witchPoison, DeathCause.WITCH_POISON);
        }
        return new NightOutcome(actions.wolfTarget(), witchSave, witchPoison, deaths);
    }

    private boolean validateAntidote(Game game, Integer wolfTarget) {
        if (!game.witchState().antidoteAvailable()) {
            throw new IllegalArgumentException("witch antidote is unavailable");
        }
        int witchSeat = game.playersByRole(RoleType.WITCH).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("witch missing"))
                .seatNo();
        if (wolfTarget == witchSeat) {
            return WitchRules.WITCH_CAN_SELF_SAVE;
        }
        return true;
    }

    private void validateTarget(Game game, Integer target) {
        if (target == null) {
            return;
        }
        if (target < 1 || target > 9 || game.playerBySeat(target).isEmpty()) {
            throw new IllegalArgumentException("invalid target seat " + target);
        }
    }
}
