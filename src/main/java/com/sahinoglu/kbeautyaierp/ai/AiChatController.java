package com.sahinoglu.kbeautyaierp.ai;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("ai")
@RequestMapping("/api/ai/chat")
public class AiChatController {
	private final AiChatService service;

	public AiChatController(AiChatService service) {
		this.service = service;
	}

	@PostMapping
	public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
		return service.chat(request);
	}
}
