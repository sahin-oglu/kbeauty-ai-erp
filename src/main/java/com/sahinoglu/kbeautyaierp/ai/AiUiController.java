package com.sahinoglu.kbeautyaierp.ai;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("ai")
@RequestMapping("/api/ai/ui")
public class AiUiController {
    private final AiUiService service;

    public AiUiController(AiUiService service) {
        this.service = service;
    }

    @PostMapping
    public UiResponse describe(@Valid @RequestBody ChatRequest request) {
        return service.describe(request);
    }
}
