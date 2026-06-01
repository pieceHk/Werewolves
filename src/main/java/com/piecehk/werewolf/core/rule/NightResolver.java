package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.DeathCause;
import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.NightActions;
import com.piecehk.werewolf.core.model.RoleType;
import com.piecehk.werewolf.core.model.WitchSelfSaveRule;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NightResolver {
    public NightOutcome resolve(Game game, NightActions actions) {
        validateTarget(game, actions.wolfTarget());
        validateTarget(game, actions.seerTarget());
        validateTarget(game, actions.witchPoison());

        if (actions.witchSave() && actions.wolfTarget() == null) {
            throw new IllegalArgumentException("witch cannot save without wolf target");
        }
        if (actions.witchSave() && actions.witchPoison() != null && !game.ruleConfig().witchBothPotionsSameNight()) {
            throw new IllegalArgumentException("witch cannot use both potions in the same night");
        }
        if (actions.witchSave()) {
            validateAntidote(game, actions.wolfTarget());
        }
        if (actions.witchPoison() != null && !game.witchState().poisonAvailable()) {
            throw new IllegalArgumentException("witch poison is unavailable");
        }

        Map<Integer, DeathCause> deaths = new LinkedHashMap<>();
        if (actions.wolfTarget() != null && !actions.witchSave()) {
            deaths.put(actions.wolfTarget(), DeathCause.WOLF_KILL);
        }
        if (actions.witchPoison() != null) {
            deaths.put(actions.witchPoison(), DeathCause.WITCH_POISON);
        }
        return new NightOutcome(actions.wolfTarget(), actions.witchSave(), actions.witchPoison(), deaths);
    }

    private void validateAntidote(Game game, Integer wolfTarget) {
        if (!game.witchState().antidoteAvailable()) {
            throw new IllegalArgumentException("witch antidote is unavailable");
        }
        int witchSeat = game.playersByRole(RoleType.WITCH).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("witch missing"))
                .seatNo();
        if (wolfTarget == witchSeat) {
            WitchSelfSaveRule rule = game.ruleConfig().witchSelfSave();
            boolean allowed = rule == WitchSelfSaveRule.ALWAYS
                    || (rule == WitchSelfSaveRule.FIRST_NIGHT_ONLY && game.roundNo() == 1);
            if (!allowed) {
                throw new IllegalArgumentException("witch self save is not allowed in round " + game.roundNo());
            }
        }
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
