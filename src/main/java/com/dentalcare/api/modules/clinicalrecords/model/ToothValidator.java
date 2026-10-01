package com.dentalcare.api.modules.clinicalrecords.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ToothValidator {

    private static final Set<String> ADULT_TEETH = Collections.unmodifiableSet(new LinkedHashSet<>(Set.of(
            "11", "12", "13", "14", "15", "16", "17", "18",
            "21", "22", "23", "24", "25", "26", "27", "28",
            "31", "32", "33", "34", "35", "36", "37", "38",
            "41", "42", "43", "44", "45", "46", "47", "48"
    )));

    private static final Set<String> CHILD_TEETH = Collections.unmodifiableSet(new LinkedHashSet<>(Set.of(
            "51", "52", "53", "54", "55",
            "61", "62", "63", "64", "65",
            "71", "72", "73", "74", "75",
            "81", "82", "83", "84", "85"
    )));

    private static final Set<String> MIXED_TEETH;

    static {
        LinkedHashSet<String> mixed = new LinkedHashSet<>(Set.of(
                "11", "12", "16", "21", "22", "26",
                "31", "32", "36", "41", "42", "46",
                "53", "54", "55", "63", "64", "65",
                "73", "74", "75", "83", "84", "85"
        ));
        MIXED_TEETH = Collections.unmodifiableSet(mixed);
    }

    private ToothValidator() {}

    public static boolean isValidTooth(DentitionType dentition, String toothCode) {
        if (toothCode == null || dentition == null) {
            return false;
        }
        String trimmed = toothCode.trim();
        return switch (dentition) {
            case ADULT -> ADULT_TEETH.contains(trimmed);
            case CHILD -> CHILD_TEETH.contains(trimmed);
            case MIXED -> ADULT_TEETH.contains(trimmed) || CHILD_TEETH.contains(trimmed);
        };
    }

    public static Set<String> getStandardTeethForDentition(DentitionType dentition) {
        return switch (dentition) {
            case ADULT -> ADULT_TEETH;
            case CHILD -> CHILD_TEETH;
            case MIXED -> MIXED_TEETH;
        };
    }
}
