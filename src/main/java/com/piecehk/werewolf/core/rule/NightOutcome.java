package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.DeathCause;

import java.util.Map;

public record NightOutcome(
        Integer wolfTarget,
        boolean saved,
        Integer poisonTarget,
        Map<Integer, DeathCause> deaths
) {
}
