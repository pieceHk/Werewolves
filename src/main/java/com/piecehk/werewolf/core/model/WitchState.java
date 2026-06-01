package com.piecehk.werewolf.core.model;

public final class WitchState {
    private boolean antidoteAvailable = true;
    private boolean poisonAvailable = true;

    public boolean antidoteAvailable() {
        return antidoteAvailable;
    }

    public boolean poisonAvailable() {
        return poisonAvailable;
    }

    public void consumeAntidote() {
        if (!antidoteAvailable) {
            throw new IllegalStateException("antidote already consumed");
        }
        antidoteAvailable = false;
    }

    public void consumePoison() {
        if (!poisonAvailable) {
            throw new IllegalStateException("poison already consumed");
        }
        poisonAvailable = false;
    }
}
