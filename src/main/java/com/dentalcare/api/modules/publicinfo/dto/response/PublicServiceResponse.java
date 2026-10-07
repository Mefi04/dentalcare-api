package com.dentalcare.api.modules.publicinfo.dto.response;

public record PublicServiceResponse(String code, String name, String category, String description, Integer durationMinutes) {
    public PublicServiceResponse(String code, String name, String category, Integer durationMinutes) {
        this(code, name, category, null, durationMinutes);
    }
}
