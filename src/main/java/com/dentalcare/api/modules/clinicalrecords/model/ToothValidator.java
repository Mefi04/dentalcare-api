package com.dentalcare.api.modules.clinicalrecords.model;

import java.util.Set;

public final class ToothValidator {

    private ToothValidator() {}

    public static boolean isValidTooth(DentitionType dentition, String toothCode) {
        return FdiToothCatalog.isValidTooth(dentition, toothCode);
    }

    public static Set<String> getStandardTeethForDentition(DentitionType dentition) {
        return FdiToothCatalog.getStandardTeethForDentition(dentition);
    }
}
