package com.piecehk.werewolf.core.event;

import com.piecehk.werewolf.core.model.GamePhase;
import com.piecehk.werewolf.core.model.RoleType;

import java.time.Instant;
import java.util.Set;

public record BasicGameEvent(
        EventType type,
        Visibility visibility,
        Set<Integer> audience,
        int roundNo,
        GamePhase phase,
        Integer publisherSeat,
        RoleType publisherRole,
        String title,
        String text,
        Instant createdAt
) implements GameEvent {
    public BasicGameEvent {
        audience = audience == null ? Set.of() : Set.copyOf(audience);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static BasicGameEvent publicEvent(EventType type, int roundNo, GamePhase phase, Integer publisherSeat,
                                             RoleType publisherRole, String title, String text) {
        return new BasicGameEvent(type, Visibility.PUBLIC, Set.of(), roundNo, phase, publisherSeat,
                publisherRole, title, text, Instant.now());
    }

    public static BasicGameEvent privateEvent(EventType type, Set<Integer> audience, int roundNo, GamePhase phase,
                                              Integer publisherSeat, RoleType publisherRole, String title, String text) {
        return new BasicGameEvent(type, Visibility.PRIVATE, audience, roundNo, phase, publisherSeat,
                publisherRole, title, text, Instant.now());
    }
}
