package com.piecehk.werewolf.core.model;

import java.util.Objects;

public final class Player {
    private final int seatNo;
    private final String name;
    private final RoleType role;
    private PlayerStatus status;
    private final String agentId;
    private final boolean human;

    public Player(int seatNo, String name, RoleType role, String agentId, boolean human) {
        if (seatNo < 1 || seatNo > 9) {
            throw new IllegalArgumentException("seatNo must be in 1..9");
        }
        this.seatNo = seatNo;
        this.name = Objects.requireNonNull(name);
        this.role = Objects.requireNonNull(role);
        this.status = PlayerStatus.ALIVE;
        this.agentId = Objects.requireNonNull(agentId);
        this.human = human;
    }

    public int seatNo() {
        return seatNo;
    }

    public String name() {
        return name;
    }

    public RoleType role() {
        return role;
    }

    public PlayerStatus status() {
        return status;
    }

    public String agentId() {
        return agentId;
    }

    public boolean human() {
        return human;
    }

    public boolean isAlive() {
        return status == PlayerStatus.ALIVE;
    }

    public void die() {
        this.status = PlayerStatus.DEAD;
    }
}
