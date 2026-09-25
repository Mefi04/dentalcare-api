package com.dentalcare.api.modules.patients.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DpiMaskerTests {

    @Test
    void masksStandardThirteenDigitDpiPreservingLastFourDigits() {
        String masked = DpiMasker.mask("2987451200101");

        assertThat(masked).isEqualTo("*********0101");
        assertThat(masked).hasSize(13);
        assertThat(masked.substring(0, 9)).isEqualTo("*********");
        assertThat(masked.substring(9)).isEqualTo("0101");
    }

    @Test
    void returnsNullWhenInputIsNull() {
        assertThat(DpiMasker.mask(null)).isNull();
    }

    @Test
    void returnsUnchangedWhenDpiLengthIsFourOrFewerDigits() {
        assertThat(DpiMasker.mask("")).isEqualTo("");
        assertThat(DpiMasker.mask("123")).isEqualTo("123");
        assertThat(DpiMasker.mask("0101")).isEqualTo("0101");
    }

    @Test
    void masksOnlyExcessDigitsWhenDpiIsLongerThanFourDigits() {
        assertThat(DpiMasker.mask("12345")).isEqualTo("*2345");
        assertThat(DpiMasker.mask("123456")).isEqualTo("**3456");
    }
}
