package com.dentalcare.api.modules.patients.util;

/**
 * Utility for masking sensitive Guatemalan DPI/CUI numbers for self-service responses.
 */
public final class DpiMasker {

    private static final int VISIBLE_DIGITS = 4;
    private static final char MASK_CHAR = '*';

    private DpiMasker() {
    }

    /**
     * Masks the DPI, exposing only the last four digits.
     * For example, "2987451200101" becomes "*********0101".
     *
     * @param dpi the raw DPI string
     * @return the masked DPI string, or null if input is null
     */
    public static String mask(String dpi) {
        if (dpi == null) {
            return null;
        }
        int length = dpi.length();
        if (length <= VISIBLE_DIGITS) {
            return dpi;
        }
        int maskedLength = length - VISIBLE_DIGITS;
        return String.valueOf(MASK_CHAR).repeat(maskedLength) + dpi.substring(maskedLength);
    }
}
