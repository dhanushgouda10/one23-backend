package com.one23.one23.ai;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiPingController {

    private final GeminiClient gemini;
    private final boolean enabled;

    public AiPingController(GeminiClient gemini, @Value("${ai.enabled}") boolean enabled) {
        this.gemini = gemini;
        this.enabled = enabled;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        if (!enabled) throw new AiUnavailableException("AI is disabled");
        return Map.of("reply", gemini.generate("Reply with one word.", "Say OK", false));
    }
}