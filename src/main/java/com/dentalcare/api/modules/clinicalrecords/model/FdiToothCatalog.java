package com.dentalcare.api.modules.clinicalrecords.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Centralized, authoritative catalog of FDI Two-Digit World Dental Federation tooth numbering,
 * anatomical definitions, dentition classifications, and allowed anatomical surfaces.
 */
public final class FdiToothCatalog {

    public record ToothDefinition(
            String code,
            int number,
            int quadrant,
            int position,
            DentitionType primaryDentition,
            boolean anterior,
            boolean maxillary,
            Set<ToothSurface> allowedSurfaces
    ) {
        public boolean isPosterior() {
            return !anterior;
        }

        public boolean isMandibular() {
            return !maxillary;
        }
    }

    private static final Map<String, ToothDefinition> CATALOG;
    private static final Set<String> ADULT_TEETH;
    private static final Set<String> CHILD_TEETH;
    private static final Set<String> MIXED_ALLOWED_TEETH;
    private static final Set<String> MIXED_STANDARD_TEETH;

    static {
        Map<String, ToothDefinition> map = new LinkedHashMap<>();

        // Quadrant 1: Maxillary Right (Permanent) 18 -> 11
        for (int pos = 8; pos >= 1; pos--) {
            registerPermanentTooth(map, 1, pos);
        }
        // Quadrant 2: Maxillary Left (Permanent) 21 -> 28
        for (int pos = 1; pos <= 8; pos++) {
            registerPermanentTooth(map, 2, pos);
        }
        // Quadrant 4: Mandibular Right (Permanent) 48 -> 41
        for (int pos = 8; pos >= 1; pos--) {
            registerPermanentTooth(map, 4, pos);
        }
        // Quadrant 3: Mandibular Left (Permanent) 31 -> 38
        for (int pos = 1; pos <= 8; pos++) {
            registerPermanentTooth(map, 3, pos);
        }

        // Deciduous Teeth
        // Quadrant 5: Maxillary Right (Deciduous) 55 -> 51
        for (int pos = 5; pos >= 1; pos--) {
            registerDeciduousTooth(map, 5, pos);
        }
        // Quadrant 6: Maxillary Left (Deciduous) 61 -> 65
        for (int pos = 1; pos <= 5; pos++) {
            registerDeciduousTooth(map, 6, pos);
        }
        // Quadrant 8: Mandibular Right (Deciduous) 85 -> 81
        for (int pos = 5; pos >= 1; pos--) {
            registerDeciduousTooth(map, 8, pos);
        }
        // Quadrant 7: Mandibular Left (Deciduous) 71 -> 75
        for (int pos = 1; pos <= 5; pos++) {
            registerDeciduousTooth(map, 7, pos);
        }

        CATALOG = Collections.unmodifiableMap(map);

        LinkedHashSet<String> adult = new LinkedHashSet<>();
        LinkedHashSet<String> child = new LinkedHashSet<>();
        LinkedHashSet<String> mixedAllowed = new LinkedHashSet<>();

        for (ToothDefinition def : CATALOG.values()) {
            if (def.primaryDentition() == DentitionType.ADULT) {
                adult.add(def.code());
                // Third molars (18, 28, 38, 48) do not erupt during mixed dentition (approx. 6-12 years)
                if (def.position() != 8) {
                    mixedAllowed.add(def.code());
                }
            } else {
                child.add(def.code());
                mixedAllowed.add(def.code());
            }
        }

        ADULT_TEETH = Collections.unmodifiableSet(adult);
        CHILD_TEETH = Collections.unmodifiableSet(child);
        MIXED_ALLOWED_TEETH = Collections.unmodifiableSet(mixedAllowed);

        // Canonical 24 teeth baseline for mixed dentition charting:
        // First permanent molars + permanent incisors (12 teeth) + deciduous canines & molars (12 teeth)
        LinkedHashSet<String> mixedStandard = new LinkedHashSet<>(List.of(
                "16", "55", "54", "53", "12", "11", "21", "22", "63", "64", "65", "26",
                "46", "85", "84", "83", "42", "41", "31", "32", "73", "74", "75", "36"
        ));
        MIXED_STANDARD_TEETH = Collections.unmodifiableSet(mixedStandard);
    }

    private static void registerPermanentTooth(Map<String, ToothDefinition> map, int quadrant, int position) {
        String code = "" + quadrant + position;
        int number = quadrant * 10 + position;
        boolean anterior = position <= 3;
        boolean maxillary = quadrant == 1 || quadrant == 2;

        Set<ToothSurface> surfaces = determineSurfaces(anterior, maxillary);
        map.put(code, new ToothDefinition(code, number, quadrant, position, DentitionType.ADULT,
                anterior, maxillary, surfaces));
    }

    private static void registerDeciduousTooth(Map<String, ToothDefinition> map, int quadrant, int position) {
        String code = "" + quadrant + position;
        int number = quadrant * 10 + position;
        boolean anterior = position <= 3;
        boolean maxillary = quadrant == 5 || quadrant == 6;

        Set<ToothSurface> surfaces = determineSurfaces(anterior, maxillary);
        map.put(code, new ToothDefinition(code, number, quadrant, position, DentitionType.CHILD,
                anterior, maxillary, surfaces));
    }

    private static Set<ToothSurface> determineSurfaces(boolean anterior, boolean maxillary) {
        LinkedHashSet<ToothSurface> surfaces = new LinkedHashSet<>();
        surfaces.add(ToothSurface.MESIAL);
        surfaces.add(ToothSurface.DISTAL);
        surfaces.add(ToothSurface.VESTIBULAR);

        if (maxillary) {
            surfaces.add(ToothSurface.PALATAL);
        } else {
            surfaces.add(ToothSurface.LINGUAL);
        }

        if (anterior) {
            surfaces.add(ToothSurface.INCISAL);
        } else {
            surfaces.add(ToothSurface.OCCLUSAL);
        }

        return Collections.unmodifiableSet(surfaces);
    }

    private FdiToothCatalog() {}

    public static boolean isValidFdiCode(String toothCode) {
        if (toothCode == null) return false;
        return CATALOG.containsKey(toothCode.trim());
    }

    public static boolean isValidTooth(DentitionType dentition, String toothCode) {
        if (dentition == null || toothCode == null) {
            return false;
        }
        String trimmed = toothCode.trim();
        return switch (dentition) {
            case ADULT -> ADULT_TEETH.contains(trimmed);
            case CHILD -> CHILD_TEETH.contains(trimmed);
            case MIXED -> MIXED_ALLOWED_TEETH.contains(trimmed);
        };
    }

    public static boolean isValidPermanentTooth(String toothCode) {
        return isValidTooth(DentitionType.ADULT, toothCode);
    }

    public static boolean isValidDeciduousTooth(String toothCode) {
        return isValidTooth(DentitionType.CHILD, toothCode);
    }

    public static boolean isAnterior(String toothCode) {
        ToothDefinition def = getDefinition(toothCode);
        return def != null && def.anterior();
    }

    public static boolean isPosterior(String toothCode) {
        ToothDefinition def = getDefinition(toothCode);
        return def != null && def.isPosterior();
    }

    public static boolean isMaxillary(String toothCode) {
        ToothDefinition def = getDefinition(toothCode);
        return def != null && def.maxillary();
    }

    public static boolean isMandibular(String toothCode) {
        ToothDefinition def = getDefinition(toothCode);
        return def != null && def.isMandibular();
    }

    public static Set<ToothSurface> allowedSurfaces(String toothCode) {
        ToothDefinition def = getDefinition(toothCode);
        return def != null ? def.allowedSurfaces() : Set.of();
    }

    public static boolean isSurfaceAllowed(String toothCode, ToothSurface surface) {
        if (surface == null) return false;
        return allowedSurfaces(toothCode).contains(surface);
    }

    public static ToothDefinition getDefinition(String toothCode) {
        if (toothCode == null) return null;
        return CATALOG.get(toothCode.trim());
    }

    public static Optional<ToothDefinition> findDefinition(String toothCode) {
        return Optional.ofNullable(getDefinition(toothCode));
    }

    public static Set<String> getStandardTeethForDentition(DentitionType dentition) {
        if (dentition == null) return ADULT_TEETH;
        return switch (dentition) {
            case ADULT -> ADULT_TEETH;
            case CHILD -> CHILD_TEETH;
            case MIXED -> MIXED_STANDARD_TEETH;
        };
    }

    public static Set<String> getAllowedTeethForDentition(DentitionType dentition) {
        if (dentition == null) return ADULT_TEETH;
        return switch (dentition) {
            case ADULT -> ADULT_TEETH;
            case CHILD -> CHILD_TEETH;
            case MIXED -> MIXED_ALLOWED_TEETH;
        };
    }
}
