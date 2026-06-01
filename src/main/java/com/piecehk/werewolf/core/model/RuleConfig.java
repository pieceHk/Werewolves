package com.piecehk.werewolf.core.model;

public record RuleConfig(
        WinCondition winCondition,
        WitchSelfSaveRule witchSelfSave,
        boolean witchBothPotionsSameNight,
        boolean hunterShootWhenPoisoned,
        boolean hunterShootWhenVoted,
        boolean allowFirstNightLastWords,
        VoteTieRule voteTie,
        int maxSpeechChars
) {
    public static RuleConfig defaults() {
        return new RuleConfig(
                WinCondition.KILL_SIDE,
                WitchSelfSaveRule.FIRST_NIGHT_ONLY,
                false,
                false,
                true,
                true,
                VoteTieRule.REVOTE_THEN_NONE,
                100
        );
    }
}
