package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NonClinicalSchedulingText {
    private static final Pattern CLINICAL_TERMS = Pattern.compile(
            "\\b(dolor|duele|sintoma|sintomas|sangrado|alergia|medicamento|medicina|infeccion|inflamacion|diagnostico|enfermedad|embarazo|fiebre|hinchazon|lesion|herida|caries|muela|muelas|diente|dientes|encia|endodoncia|extraccion|protesis|ortodoncia|implante|pain|bleeding|allergy|medication|infection|swelling|diagnosis|disease|wound|tooth)\\b");
    private NonClinicalSchedulingText() {}

    public static void validate(String value) {
        if (!isSafe(value)) {
            throw new BadRequestException("Reason must contain scheduling information only; do not include clinical details");
        }
    }

    public static boolean isSafe(String value) {
        if (value == null || value.isBlank()) return true;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return !CLINICAL_TERMS.matcher(normalized).find();
    }
}
