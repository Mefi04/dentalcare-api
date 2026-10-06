package com.dentalcare.api.modules.contactinquiries.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ContactInquiryReason {
    GENERAL,
    SERVICES,
    PROFESSIONALS,
    LOCATIONS,
    APPOINTMENT_HELP,
    ACCOUNT_ACTIVATION,
    OTHER;

    @JsonCreator
    public static ContactInquiryReason fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        for (ContactInquiryReason reason : values()) {
            if (reason.name().equalsIgnoreCase(normalized)) {
                return reason;
            }
        }
        return switch (normalized.toLowerCase()) {
            case "general" -> GENERAL;
            case "servicios" -> SERVICES;
            case "odontologos" -> PROFESSIONALS;
            case "sucursales" -> LOCATIONS;
            case "cita" -> APPOINTMENT_HELP;
            case "activar" -> ACCOUNT_ACTIVATION;
            case "otro" -> OTHER;
            default -> throw new IllegalArgumentException("Unknown contact inquiry reason: " + value);
        };
    }
}
