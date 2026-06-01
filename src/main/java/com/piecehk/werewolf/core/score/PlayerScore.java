package com.piecehk.werewolf.core.score;

import com.piecehk.werewolf.core.model.Camp;
import com.piecehk.werewolf.core.model.PlayerStatus;
import com.piecehk.werewolf.core.model.RoleType;

public record PlayerScore(
        int rank,
        int seat,
        RoleType role,
        Camp camp,
        PlayerStatus status,
        double score,
        double delta
) {
}
