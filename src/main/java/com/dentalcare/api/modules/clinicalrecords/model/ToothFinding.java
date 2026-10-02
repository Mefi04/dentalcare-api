package com.dentalcare.api.modules.clinicalrecords.model;

public enum ToothFinding {
    HEALTHY,
    CARIOUS,
    TREATED,
    MISSING,
    TO_TREAT,
    RESTORED,
    CROWN,
    IMPLANT,
    ENDODONTIC,
    FRACTURE,
    PROSTHESIS;

    public boolean isToothLevelOnly() {
        return this == MISSING
                || this == IMPLANT
                || this == CROWN
                || this == ENDODONTIC
                || this == PROSTHESIS;
    }

    public boolean isSurfaceLevelOnly() {
        return this == CARIOUS;
    }

    public boolean allowsToothLevel() {
        return !isSurfaceLevelOnly();
    }

    public boolean allowsSurfaceLevel() {
        return !isToothLevelOnly();
    }
}
