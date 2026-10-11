package com.dentalcare.api.modules.assistant.controller;

import com.dentalcare.api.modules.assistant.dto.request.PublicAssistantRequest;
import com.dentalcare.api.modules.assistant.dto.response.PublicAssistantResponse;
import com.dentalcare.api.modules.assistant.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/assistant/messages")
@Tag(name = "Public assistant", description = "AI-assisted public guidance and first appointment assistant")
public class PublicAssistantController {

    private final AssistantService assistantService;

    public PublicAssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping
    @Operation(summary = "Send a message to the AI virtual assistant",
            description = "Public endpoint with rate limiting (20 requests per 15 min per IP). Explicit consent (aiProcessingAccepted: true) is required.")
    public ResponseEntity<PublicAssistantResponse> sendMessage(
            @Valid @RequestBody PublicAssistantRequest request) {
        return ResponseEntity.ok(assistantService.processMessage(request));
    }
}
