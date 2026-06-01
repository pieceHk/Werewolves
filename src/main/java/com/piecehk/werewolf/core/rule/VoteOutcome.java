package com.piecehk.werewolf.core.rule;

import java.util.List;
import java.util.Map;

public record VoteOutcome(
        Map<Integer, Long> counts,
        List<Integer> tiedSeats,
        Integer exiledSeat,
        boolean requiresRevote
) {
}
