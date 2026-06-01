package com.piecehk.werewolf.game.player;

import com.piecehk.werewolf.agent.ActionParser;
import com.piecehk.werewolf.agent.AgentContext;
import com.piecehk.werewolf.agent.ParsedAction;
import com.piecehk.werewolf.agent.PromptBuilder;
import com.piecehk.werewolf.agent.action.ActionType;
import com.piecehk.werewolf.agent.llm.ChatOptions;
import com.piecehk.werewolf.agent.llm.LLMClient;

import java.util.Set;

public final class LLMPlayerController implements PlayerController {
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ActionParser actionParser;
    private final ChatOptions chatOptions;

    public LLMPlayerController(LLMClient llmClient, PromptBuilder promptBuilder, ActionParser actionParser, ChatOptions chatOptions) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.actionParser = actionParser;
        this.chatOptions = chatOptions;
    }

    @Override
    public ParsedAction decide(AgentContext context, ActionType required, Set<Integer> validTargets, int maxSpeechChars) {
        String raw = llmClient.chat(promptBuilder.build(context, required, maxSpeechChars), chatOptions);
        return actionParser.parse(raw, required, validTargets, maxSpeechChars);
    }

    @Override
    public boolean isHuman() {
        return false;
    }
}
