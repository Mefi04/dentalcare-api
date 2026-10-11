package com.dentalcare.api.modules.assistant.service;

import com.dentalcare.api.modules.assistant.dto.request.PublicAssistantRequest;
import com.dentalcare.api.modules.assistant.dto.response.PublicAssistantResponse;

public interface AssistantService {

    PublicAssistantResponse processMessage(PublicAssistantRequest request);
}
