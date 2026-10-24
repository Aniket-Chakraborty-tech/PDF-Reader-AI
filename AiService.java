package com.pdfreader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Talks to the Claude API. The key is read from the ANTHROPIC_API_KEY
 * environment variable so it never ends up in the source code or on GitHub.
 */
public class AiService {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String SYSTEM_PROMPT =
            "You are a friendly reading assistant inside a PDF reader. "
            + "Explain words and ideas clearly and briefly, in plain language. "
            + "Do not use markdown headings or tables.";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final String apiKey = System.getenv("ANTHROPIC_API_KEY");
    private final String model = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Explains a word or short phrase, using the surrounding text (if we have it) for context. */
    public String explain(String word, String context) throws IOException, InterruptedException {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Explain \"").append(word).append("\".\n");
        if (context != null && !context.isBlank()) {
            prompt.append("It appeared in this text from a PDF: \"").append(context).append("\"\n");
        }
        prompt.append("\nGive me:\n")
              .append("1. The meaning in simple words.\n");
        if (context != null && !context.isBlank()) {
            prompt.append("2. What it means in this particular text.\n")
                  .append("3. One short example sentence.");
        } else {
            prompt.append("2. One short example sentence.");
        }
        return ask(prompt.toString());
    }

    /** Sends any question to the model and returns the text of the answer. */
    public String ask(String userMessage) throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set.");
        }

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", userMessage);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 700);
        body.addProperty("system", SYSTEM_PROMPT);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("AI request failed (HTTP " + response.statusCode() + "): " + response.body());
        }
        return readText(response.body());
    }

    private String readText(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        StringBuilder answer = new StringBuilder();
        for (JsonElement element : root.getAsJsonArray("content")) {
            JsonObject block = element.getAsJsonObject();
            if ("text".equals(block.get("type").getAsString())) {
                answer.append(block.get("text").getAsString());
            }
        }
        return answer.toString().trim();
    }
}
