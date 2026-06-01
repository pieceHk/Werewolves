package com.piecehk.werewolf.core.event;

import com.piecehk.werewolf.core.model.GamePhase;
import com.piecehk.werewolf.core.model.RoleType;

import java.time.Instant;
import java.util.Set;

public sealed interface GameEvent permits BasicGameEvent {
    EventType type();

    Visibility visibility();

    Set<Integer> audience();

    int roundNo();

    GamePhase phase();

    Integer publisherSeat();

    RoleType publisherRole();

    String title();

    String text();

    Instant createdAt();

    default boolean visibleTo(int seatNo) {
        return visibility() == Visibility.PUBLIC || audience().contains(seatNo);
    }
}
