package com.piecehk.werewolf.core.score;

public record ScoreConfig(
        double base,
        double survivePerRound,
        double surviveCap,
        double voteHitWolf,
        double voteMiss,
        double abstain,
        double invalidSpeech,
        double seerKillHit,
        double seerClearCorrect,
        double witchPoisonHit,
        double witchPoisonMiss,
        double sheriffGood,
        double sheriffWolf,
        double winBonus
) {
    public static ScoreConfig defaults() {
        return new ScoreConfig(5.0, 0.2, 1.5, 0.6, -0.4, -0.1, -0.2,
                1.0, 0.3, 1.0, -0.8, 0.3, 1.0, 1.5);
    }
}
