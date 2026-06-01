package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.agent.action.ActionType;
import com.piecehk.werewolf.agent.action.SpeakAction;
import com.piecehk.werewolf.agent.action.VoteAction;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActionParserTest {
    private final ActionParser parser = new ActionParser();

    @Test
    void stripsMarkdownFenceAndParsesVote() {
        ParsedAction parsed = parser.parse("""
                ```json
                {"reasoning":"怀疑3","action":{"type":"VOTE","targetSeat":3}}
                ```
                """, ActionType.VOTE, Set.of(2, 3, 4), 100);

        assertThat(parsed.action()).isInstanceOf(VoteAction.class);
        assertThat(((VoteAction) parsed.action()).targetSeat()).isEqualTo(3);
        assertThat(parsed.reasoning()).isEqualTo("怀疑3");
    }

    @Test
    void truncatesLongSpeechByCodePoints() {
        String speech = "好".repeat(105);

        ParsedAction parsed = parser.parse(
                "{\"reasoning\":\"超长\",\"action\":{\"type\":\"SPEAK\",\"speech\":\"" + speech + "\"}}",
                ActionType.SPEAK, Set.of(), 100);

        assertThat(parsed.action()).isInstanceOf(SpeakAction.class);
        assertThat(((SpeakAction) parsed.action()).speech().codePointCount(0, ((SpeakAction) parsed.action()).speech().length()))
                .isEqualTo(100);
        assertThat(parsed.warning()).contains("截断");
    }

    @Test
    void downgradesInvalidTargetToAbstain() {
        ParsedAction parsed = parser.parse(
                "{\"reasoning\":\"越界\",\"action\":{\"type\":\"VOTE\",\"targetSeat\":99}}",
                ActionType.VOTE, Set.of(1, 2), 100);

        assertThat(((VoteAction) parsed.action()).targetSeat()).isNull();
    }
}
