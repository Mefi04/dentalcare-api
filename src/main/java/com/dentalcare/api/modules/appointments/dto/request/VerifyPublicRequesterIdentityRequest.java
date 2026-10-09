package com.dentalcare.api.modules.appointments.dto.request;

import jakarta.validation.constraints.NotNull;

public record VerifyPublicRequesterIdentityRequest(@NotNull VerificationMethod method) {
    public enum VerificationMethod {
        IN_PERSON,
        CALLBACK_TO_REGISTERED_CONTACT,
        DOCUMENT_REVIEW
    }
}
