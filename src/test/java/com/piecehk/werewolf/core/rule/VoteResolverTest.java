package com.piecehk.werewolf.core.rule;

import com.piecehk.werewolf.core.model.RuleConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoteResolverTest {
    private final VoteResolver resolver = new VoteResolver();

    @Test
    void exilesMajorityTarget() {
        VoteOutcome outcome = resolver.resolve(Map.of(1, 3, 2, 3, 4, 5), RuleConfig.defaults(), false);

        assertThat(outcome.exiledSeat()).isEqualTo(3);
        assertThat(outcome.requiresRevote()).isFalse();
    }

    @Test
    void firstTieRequiresRevoteAndSecondTieExilesNobody() {
        VoteOutcome first = resolver.resolve(Map.of(1, 3, 2, 4), RuleConfig.defaults(), false);
        VoteOutcome second = resolver.resolve(Map.of(1, 3, 2, 4), RuleConfig.defaults(), true);

        assertThat(first.requiresRevote()).isTrue();
        assertThat(first.exiledSeat()).isNull();
        assertThat(second.requiresRevote()).isFalse();
        assertThat(second.exiledSeat()).isNull();
    }
}
