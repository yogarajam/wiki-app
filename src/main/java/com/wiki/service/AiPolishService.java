package com.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiPolishService {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";

    private static final String SYSTEM_PROMPT = """
            You are a professional editor. Your task is to polish the given text for grammar, \
            clarity, conciseness, and flow.

            You will receive content in Editor.js JSON format (an object with a "blocks" array). \
            Each block has a "type" and "data" field.

            Rules:
            - Only modify text content in "paragraph", "header", "list", and "quote" blocks.
            - Preserve all block types, their order, and structure exactly.
            - Do NOT modify "code", "image", "table", "linkTool", or any other non-text block types.
            - For "list" blocks, polish each item text individually.
            - For "quote" blocks, polish the "text" field but keep "caption" unchanged.
            - Preserve any HTML inline formatting (bold, italic, links, markers) within text.
            - Return ONLY the valid Editor.js JSON object — no markdown fences, no explanation.
            """;

    @Value("${claude.api-key:}")
    private String apiKey;

    @Value("${claude.model:claude-sonnet-4-20250514}")
    private String model;

    @Value("${claude.max-tokens:4096}")
    private int maxTokens;

    @Value("${claude.timeout:30}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String polishContent(String editorJsonContent) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Claude API key is not configured. Set the CLAUDE_API_KEY environment variable.");
        }

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(Duration.ofSeconds(10));
            setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        }});

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", SYSTEM_PROMPT,
                "messages", List.of(
                        Map.of("role", "user", "content", editorJsonContent)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    ANTHROPIC_API_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new RuntimeException("Claude API returned status: " + response.getStatusCode());
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String polishedContent = root
                    .path("content")
                    .path(0)
                    .path("text")
                    .asText();

            if (polishedContent == null || polishedContent.isBlank()) {
                throw new RuntimeException("Claude returned empty content");
            }

            // Strip markdown code fences if present
            polishedContent = polishedContent.trim();
            if (polishedContent.startsWith("```json")) {
                polishedContent = polishedContent.substring(7);
            } else if (polishedContent.startsWith("```")) {
                polishedContent = polishedContent.substring(3);
            }
            if (polishedContent.endsWith("```")) {
                polishedContent = polishedContent.substring(0, polishedContent.length() - 3);
            }
            polishedContent = polishedContent.trim();

            // Validate it's valid JSON with a blocks array
            JsonNode polishedJson = objectMapper.readTree(polishedContent);
            if (!polishedJson.has("blocks") || !polishedJson.get("blocks").isArray()) {
                throw new RuntimeException("Claude response is not valid Editor.js JSON (missing blocks array)");
            }

            return polishedContent;

        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Claude API timeout or connection error", e);
            throw new RuntimeException("Claude API is unavailable. Please try again later.", e);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Claude API client error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Claude API error: " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling Claude API", e);
            throw new RuntimeException("Failed to polish content: " + e.getMessage(), e);
        }
    }
}
