package com.piecehk.werewolf.agent;

import com.piecehk.werewolf.agent.action.ActionType;
import com.piecehk.werewolf.agent.action.SeerCheckAction;
import com.piecehk.werewolf.agent.action.SpeakAction;
import com.piecehk.werewolf.agent.action.VoteAction;
import com.piecehk.werewolf.agent.action.WolfKillAction;
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

    @Test
    void recoversWolfKillTargetFromReasoningWhenFieldMissing() {
        ParsedAction parsed = parser.parse(
                "{\"reasoning\":\"首夜统一刀法，建议刀4号，制造混乱且不碰疑似神职位\",\"action\":{\"type\":\"WOLF_KILL\"}}",
                ActionType.WOLF_KILL, Set.of(4, 5, 7), 100);

        assertThat(parsed.action()).isInstanceOf(WolfKillAction.class);
        assertThat(((WolfKillAction) parsed.action()).targetSeat()).isEqualTo(4);
        assertThat(parsed.warning()).contains("回填");
    }

    @Test
    void recoversSeerCheckTargetFromReasoningWhenFieldNull() {
        ParsedAction parsed = parser.parse(
                "{\"reasoning\":\"首夜优先查验高危位，选1号玩家试探身份\",\"action\":{\"type\":\"SEER_CHECK\",\"targetSeat\":null}}",
                ActionType.SEER_CHECK, Set.of(1, 2, 3), 100);

        assertThat(((SeerCheckAction) parsed.action()).targetSeat()).isEqualTo(1);
    }

    @Test
    void doesNotRecoverWhenReasoningOnlyMentionsSeatsToAvoid() {
        // “避免动…位1、2、5、7”不是击杀意图，不应被误回填；真空刀应保持空刀
        ParsedAction parsed = parser.parse(
                "{\"reasoning\":\"首夜空刀，避免动预言家热门位1、2、5、7\",\"action\":{\"type\":\"WOLF_KILL\"}}",
                ActionType.WOLF_KILL, Set.of(1, 2, 5, 7, 8), 100);

        assertThat(((WolfKillAction) parsed.action()).targetSeat()).isNull();
    }
}
