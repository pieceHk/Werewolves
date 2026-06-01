package com.piecehk.werewolf.core.model;

public record RuleConfig(
        WinCondition winCondition,
        boolean hunterShootWhenPoisoned,
        boolean hunterShootWhenVoted,
        boolean allowFirstNightLastWords,
        VoteTieRule voteTie,
        boolean sheriffEnabled,
        double sheriffVoteWeight,
        int maxSpeechChars
) {
    public static RuleConfig defaults() {
        return new RuleConfig(
                WinCondition.KILL_SIDE,
                false,
                true,
                true,
                VoteTieRule.REVOTE_THEN_NONE,
                true,
                1.5,
                100
        );
    }
}
