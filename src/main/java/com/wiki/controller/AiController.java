package com.wiki.controller;

import com.wiki.service.AiPolishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiPolishService aiPolishService;

    @PostMapping("/polish")
    public ResponseEntity<?> polishContent(@RequestBody Map<String, String> request) {
        String content = request.get("content");

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Content is required"));
        }

        try {
            String polished = aiPolishService.polishContent(content);
            return ResponseEntity.ok(Map.of("content", polished));
        } catch (IllegalStateException e) {
            // API key not configured
            return ResponseEntity.status(503)
                    .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("unavailable")) {
                return ResponseEntity.status(503)
                        .body(Map.of("error", e.getMessage()));
            }
            log.error("Error polishing content", e);
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", msg));
        }
    }
}
