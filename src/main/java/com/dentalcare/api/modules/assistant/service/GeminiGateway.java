package com.dentalcare.api.modules.assistant.service;

public interface GeminiGateway {
    String generate(String instruction, String prompt);
}
