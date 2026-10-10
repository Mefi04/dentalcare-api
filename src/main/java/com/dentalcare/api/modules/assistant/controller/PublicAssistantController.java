package com.dentalcare.api.modules.assistant.controller;

import com.dentalcare.api.modules.assistant.dto.request.PublicAssistantRequest;
import com.dentalcare.api.modules.assistant.dto.response.PublicAssistantResponse;
import com.dentalcare.api.modules.assistant.service.PublicAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/assistant/messages")
@Tag(name = "Public assistant")
public class PublicAssistantController {
    private final PublicAssistantService service;

    public PublicAssistantController(PublicAssistantService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Ask the public assistant about clinic information, first appointments or general dental health",
            description = "Do not submit personal identifiers or patient records. The assistant does not diagnose, prescribe or book appointments.")
    public PublicAssistantResponse answer(@Valid @RequestBody PublicAssistantRequest request) {
        return service.answer(request);
    }
}
