package com.piecehk.werewolf.core.model;

public enum RoleType {
    WEREWOLF(Camp.WEREWOLF),
    VILLAGER(Camp.GOOD),
    SEER(Camp.GOOD),
    WITCH(Camp.GOOD),
    HUNTER(Camp.GOOD);

    private final Camp camp;

    RoleType(Camp camp) {
        this.camp = camp;
    }

    public Camp camp() {
        return camp;
    }

    public boolean isGod() {
        return this == SEER || this == WITCH || this == HUNTER;
    }

    public String displayName() {
        return switch (this) {
            case WEREWOLF -> "狼人";
            case VILLAGER -> "村民";
            case SEER -> "预言家";
            case WITCH -> "女巫";
            case HUNTER -> "猎人";
        };
    }
}
