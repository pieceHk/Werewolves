package com.piecehk.werewolf.core.statemachine;

import com.piecehk.werewolf.core.model.Game;
import com.piecehk.werewolf.core.model.GameFactory;
import com.piecehk.werewolf.core.model.GamePhase;
import com.piecehk.werewolf.core.model.RuleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateMachineTest {
    private final GameStateMachine stateMachine = new GameStateMachine();

    @Test
    void allowsConfiguredPhaseFlow() {
        Game game = GameFactory.standardNine("match-test", 123L, RuleConfig.defaults());

        stateMachine.transition(game, GamePhase.NIGHT);
        stateMachine.transition(game, GamePhase.DAY_ANNOUNCE);
        stateMachine.transition(game, GamePhase.DAY_DISCUSS);
        stateMachine.transition(game, GamePhase.DAY_VOTE);
        stateMachine.transition(game, GamePhase.NIGHT);

        assertThat(game.phase()).isEqualTo(GamePhase.NIGHT);
    }

    @Test
    void rejectsIllegalTransition() {
        Game game = GameFactory.standardNine("match-test", 123L, RuleConfig.defaults());

        assertThatThrownBy(() -> stateMachine.transition(game, GamePhase.DAY_VOTE))
                .isInstanceOf(IllegalPhaseTransitionException.class);
    }
}
