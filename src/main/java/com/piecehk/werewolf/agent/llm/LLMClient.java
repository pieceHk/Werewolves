package com.piecehk.werewolf.agent.llm;

import java.util.List;
import java.util.function.Consumer;

public interface LLMClient {
    String chat(List<ChatMessage> messages, ChatOptions options);

    default void chatStream(List<ChatMessage> messages, ChatOptions options, Consumer<String> onDelta, Runnable onComplete) {
        onDelta.accept(chat(messages, options));
        onComplete.run();
    }
}
