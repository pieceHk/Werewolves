package com.piecehk.werewolf.core.score;

import java.util.List;

public record ScoreboardRound(
        int roundNo,
        List<PlayerScore> scores
) {
}
