package com.piecehk.werewolf.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AgentJournal {
    private final Map<Integer, List<String>> entriesBySeat = new HashMap<>();

    public void append(int seatNo, String entry) {
        entriesBySeat.computeIfAbsent(seatNo, ignored -> new ArrayList<>()).add(entry);
    }

    public String read(int seatNo) {
        return String.join("\n", entriesBySeat.getOrDefault(seatNo, List.of()));
    }

    public List<String> entries(int seatNo) {
        return List.copyOf(entriesBySeat.getOrDefault(seatNo, List.of()));
    }
}
