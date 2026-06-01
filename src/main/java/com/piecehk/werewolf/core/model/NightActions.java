package com.piecehk.werewolf.core.model;

public record NightActions(
        Integer wolfTarget,
        Integer seerTarget,
        boolean witchSave,
        Integer witchPoison
) {
    public static NightActions none() {
        return new NightActions(null, null, false, null);
    }
}
