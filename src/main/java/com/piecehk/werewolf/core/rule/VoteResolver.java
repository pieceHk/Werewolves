package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.RuleConfig;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class VoteResolver {
    public VoteOutcome resolve(Map<Integer, Integer> votes, RuleConfig ruleConfig, boolean revote) {
        Map<Integer, Long> counts = votes.values().stream()
                .filter(target -> target != null)
                .collect(Collectors.groupingBy(target -> target, Collectors.counting()));
        if (counts.isEmpty()) {
            return new VoteOutcome(counts, List.of(), null, false);
        }
        long max = counts.values().stream().max(Comparator.naturalOrder()).orElse(0L);
        List<Integer> tied = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (tied.size() == 1) {
            return new VoteOutcome(counts, tied, tied.get(0), false);
        }
        return new VoteOutcome(counts, tied, null, !revote);
    }
}
