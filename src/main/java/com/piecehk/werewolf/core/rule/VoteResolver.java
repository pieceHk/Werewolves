package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.RuleConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoteResolver {
    public VoteOutcome resolve(Map<Integer, Integer> votes, RuleConfig ruleConfig, boolean revote) {
        return resolve(votes, ruleConfig, revote, null);
    }

    public VoteOutcome resolve(Map<Integer, Integer> votes, RuleConfig ruleConfig, boolean revote, Integer sheriffSeat) {
        Map<Integer, Double> counts = new HashMap<>();
        for (Map.Entry<Integer, Integer> vote : votes.entrySet()) {
            Integer target = vote.getValue();
            if (target == null) {
                continue;
            }
            double weight = sheriffSeat != null && sheriffSeat.equals(vote.getKey())
                    ? ruleConfig.sheriffVoteWeight()
                    : 1.0;
            counts.merge(target, weight, Double::sum);
        }
        if (counts.isEmpty()) {
            return new VoteOutcome(counts, List.of(), null, false);
        }
        double max = counts.values().stream().max(Double::compareTo).orElse(0.0);
        List<Integer> tied = counts.entrySet().stream()
                .filter(entry -> Double.compare(entry.getValue(), max) == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (tied.size() == 1) {
            return new VoteOutcome(counts, tied, tied.get(0), false);
        }
        return new VoteOutcome(counts, tied, null, !revote);
    }
}
