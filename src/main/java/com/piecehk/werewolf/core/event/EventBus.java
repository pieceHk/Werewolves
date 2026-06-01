package com.piecehk.werewolf.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class EventBus {
    private final List<Consumer<GameEvent>> subscribers = new ArrayList<>();

    public void subscribe(Consumer<GameEvent> subscriber) {
        subscribers.add(subscriber);
    }

    public void publish(GameEvent event) {
        subscribers.forEach(subscriber -> subscriber.accept(event));
    }
}
