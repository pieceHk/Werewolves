package com.piecehk.werewolf.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

public final class QwenLLMClient implements LLMClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final int maxRetries;
    private final Semaphore limiter;

    public QwenLLMClient(String baseUrl, String apiKey, Duration connectTimeout, int maxRetries, int maxConcurrency) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LLMException("DASHSCOPE_API_KEY is required for QwenLLMClient");
        }
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.maxRetries = Math.max(1, maxRetries);
        this.limiter = new Semaphore(Math.max(1, maxConcurrency));
    }

    public static QwenLLMClient fromEnvironment(String baseUrl, Duration connectTimeout, int maxRetries, int maxConcurrency) {
        return new QwenLLMClient(baseUrl, System.getenv("DASHSCOPE_API_KEY"), connectTimeout, maxRetries, maxConcurrency);
    }

    @Override
    public String chat(List<ChatMessage> messages, ChatOptions options) {
        try {
            limiter.acquire();
            return doChatWithRetries(messages, options);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("qwen request interrupted", e);
        } finally {
            limiter.release();
        }
    }

    private String doChatWithRetries(List<ChatMessage> messages, ChatOptions options) {
        LLMException last = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doChat(messages, options);
            } catch (LLMException e) {
                last = e;
                if (attempt < maxRetries) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw last == null ? new LLMException("qwen request failed") : last;
    }

    private String doChat(List<ChatMessage> messages, ChatOptions options) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", options.model());
            body.put("temperature", options.temperature());
            body.put("stream", false);
            body.put("messages", messages.stream()
                    .map(message -> Map.of("role", message.role(), "content", message.content()))
                    .toList());
            body.putAll(options.extra());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(options.timeout())
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500 || response.statusCode() == 429) {
                throw new LLMException("qwen transient error status=" + response.statusCode());
            }
            if (response.statusCode() >= 400) {
                throw new LLMException("qwen request rejected status=" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (IOException e) {
            throw new LLMException("qwen request io failure", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("qwen request interrupted", e);
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Math.min(1000L * attempt, 3000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("qwen retry interrupted", e);
        }
    }
}
