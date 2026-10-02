package com.dentalcare.api.modules.clinicalrecords.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FdiToothCatalogTests {

    @Nested
    @DisplayName("Permanent Dentition (ADULT)")
    class PermanentDentitionTests {

        @Test
        void standardTeethHasAll32PermanentTeeth() {
            Set<String> standardTeeth = FdiToothCatalog.getStandardTeethForDentition(DentitionType.ADULT);
            assertThat(standardTeeth).hasSize(32);
            assertThat(standardTeeth).contains(
                    "11", "12", "13", "14", "15", "16", "17", "18",
                    "21", "22", "23", "24", "25", "26", "27", "28",
                    "31", "32", "33", "34", "35", "36", "37", "38",
                    "41", "42", "43", "44", "45", "46", "47", "48"
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "11", "12", "13", "14", "15", "16", "17", "18",
                "21", "22", "23", "24", "25", "26", "27", "28",
                "31", "32", "33", "34", "35", "36", "37", "38",
                "41", "42", "43", "44", "45", "46", "47", "48"
        })
        void acceptsAllPermanentTeethInAdultDentition(String toothCode) {
            assertThat(FdiToothCatalog.isValidPermanentTooth(toothCode)).isTrue();
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.ADULT, toothCode)).isTrue();
            assertThat(FdiToothCatalog.isValidFdiCode(toothCode)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"10", "19", "20", "29", "30", "39", "40", "49", "99", "0", "abc", ""})
        void rejectsInvalidCodesForPermanentDentition(String invalidCode) {
            assertThat(FdiToothCatalog.isValidPermanentTooth(invalidCode)).isFalse();
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.ADULT, invalidCode)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"51", "55", "61", "65", "71", "75", "81", "85"})
        void rejectsChildTeethInAdultDentition(String childCode) {
            assertThat(FdiToothCatalog.isValidPermanentTooth(childCode)).isFalse();
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.ADULT, childCode)).isFalse();
        }
    }

    @Nested
    @DisplayName("Deciduous Dentition (CHILD)")
    class DeciduousDentitionTests {

        @Test
        void standardTeethHasAll20DeciduousTeeth() {
            Set<String> standardTeeth = FdiToothCatalog.getStandardTeethForDentition(DentitionType.CHILD);
            assertThat(standardTeeth).hasSize(20);
            assertThat(standardTeeth).contains(
                    "51", "52", "53", "54", "55",
                    "61", "62", "63", "64", "65",
                    "71", "72", "73", "74", "75",
                    "81", "82", "83", "84", "85"
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "51", "52", "53", "54", "55",
                "61", "62", "63", "64", "65",
                "71", "72", "73", "74", "75",
                "81", "82", "83", "84", "85"
        })
        void acceptsAllDeciduousTeethInChildDentition(String toothCode) {
            assertThat(FdiToothCatalog.isValidDeciduousTooth(toothCode)).isTrue();
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.CHILD, toothCode)).isTrue();
            assertThat(FdiToothCatalog.isValidFdiCode(toothCode)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"50", "56", "60", "66", "70", "76", "80", "86", "99", "0"})
        void rejectsInvalidCodesForDeciduousDentition(String invalidCode) {
            assertThat(FdiToothCatalog.isValidDeciduousTooth(invalidCode)).isFalse();
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.CHILD, invalidCode)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"11", "16", "21", "26", "31", "36", "41", "46"})
        void rejectsAdultTeethInChildDentition(String adultCode) {
            assertThat(FdiToothCatalog.isValidDeciduousTooth(adultCode)).isFalse();
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.CHILD, adultCode)).isFalse();
        }
    }

    @Nested
    @DisplayName("Mixed Dentition (MIXED)")
    class MixedDentitionTests {

        @Test
        void allowedTeethHasAll48PossibleTeethForMixedDentition() {
            Set<String> allowedTeeth = FdiToothCatalog.getAllowedTeethForDentition(DentitionType.MIXED);
            assertThat(allowedTeeth).hasSize(48);
            // Must contain deciduous teeth
            assertThat(allowedTeeth).contains("51", "55", "61", "65", "71", "75", "81", "85");
            // Must contain permanent teeth except 3rd molars
            assertThat(allowedTeeth).contains("11", "17", "21", "27", "31", "37", "41", "47");
            // Must NOT contain 3rd molars
            assertThat(allowedTeeth).doesNotContain("18", "28", "38", "48");

            Set<String> standardTeeth = FdiToothCatalog.getStandardTeethForDentition(DentitionType.MIXED);
            assertThat(standardTeeth).hasSize(24);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "51", "55", "63", "74", "85",
                "11", "16", "17", "21", "26", "31", "36", "41", "47"
        })
        void acceptsDeciduousAndPermanentInMixedDentition(String toothCode) {
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.MIXED, toothCode)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"18", "28", "38", "48"})
        void rejectsThirdMolarsInMixedDentition(String thirdMolar) {
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.MIXED, thirdMolar)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"99", "56", "0", "abc"})
        void rejectsInvalidCodesInMixedDentition(String invalidCode) {
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.MIXED, invalidCode)).isFalse();
        }
    }

    @Nested
    @DisplayName("Anterior vs Posterior Teeth & Surfaces")
    class AnteriorPosteriorTests {

        @ParameterizedTest
        @ValueSource(strings = {"11", "12", "13", "21", "22", "23", "31", "32", "33", "41", "42", "43", "51", "62", "73", "81"})
        void identifiesAnteriorTeethCorrectly(String toothCode) {
            assertThat(FdiToothCatalog.isAnterior(toothCode)).isTrue();
            assertThat(FdiToothCatalog.isPosterior(toothCode)).isFalse();

            Set<ToothSurface> surfaces = FdiToothCatalog.allowedSurfaces(toothCode);
            assertThat(surfaces).contains(ToothSurface.INCISAL);
            assertThat(surfaces).doesNotContain(ToothSurface.OCCLUSAL);
            assertThat(FdiToothCatalog.isSurfaceAllowed(toothCode, ToothSurface.INCISAL)).isTrue();
            assertThat(FdiToothCatalog.isSurfaceAllowed(toothCode, ToothSurface.OCCLUSAL)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"14", "15", "16", "17", "18", "24", "26", "35", "37", "44", "46", "54", "55", "64", "75", "84"})
        void identifiesPosteriorTeethCorrectly(String toothCode) {
            assertThat(FdiToothCatalog.isPosterior(toothCode)).isTrue();
            assertThat(FdiToothCatalog.isAnterior(toothCode)).isFalse();

            Set<ToothSurface> surfaces = FdiToothCatalog.allowedSurfaces(toothCode);
            assertThat(surfaces).contains(ToothSurface.OCCLUSAL);
            assertThat(surfaces).doesNotContain(ToothSurface.INCISAL);
            assertThat(FdiToothCatalog.isSurfaceAllowed(toothCode, ToothSurface.OCCLUSAL)).isTrue();
            assertThat(FdiToothCatalog.isSurfaceAllowed(toothCode, ToothSurface.INCISAL)).isFalse();
        }
    }

    @Nested
    @DisplayName("Maxillary vs Mandibular Quadrants & Lingual/Palatal Surfaces")
    class MaxillaryMandibularTests {

        @ParameterizedTest
        @ValueSource(strings = {"11", "16", "21", "25", "51", "54", "61", "65"})
        void maxillaryTeethAllowPalatalAndRejectLingual(String upperTooth) {
            assertThat(FdiToothCatalog.isMaxillary(upperTooth)).isTrue();
            assertThat(FdiToothCatalog.isMandibular(upperTooth)).isFalse();

            Set<ToothSurface> surfaces = FdiToothCatalog.allowedSurfaces(upperTooth);
            assertThat(surfaces).contains(ToothSurface.PALATAL);
            assertThat(surfaces).doesNotContain(ToothSurface.LINGUAL);
            assertThat(FdiToothCatalog.isSurfaceAllowed(upperTooth, ToothSurface.PALATAL)).isTrue();
            assertThat(FdiToothCatalog.isSurfaceAllowed(upperTooth, ToothSurface.LINGUAL)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"31", "36", "41", "45", "71", "74", "81", "85"})
        void mandibularTeethAllowLingualAndRejectPalatal(String lowerTooth) {
            assertThat(FdiToothCatalog.isMandibular(lowerTooth)).isTrue();
            assertThat(FdiToothCatalog.isMaxillary(lowerTooth)).isFalse();

            Set<ToothSurface> surfaces = FdiToothCatalog.allowedSurfaces(lowerTooth);
            assertThat(surfaces).contains(ToothSurface.LINGUAL);
            assertThat(surfaces).doesNotContain(ToothSurface.PALATAL);
            assertThat(FdiToothCatalog.isSurfaceAllowed(lowerTooth, ToothSurface.LINGUAL)).isTrue();
            assertThat(FdiToothCatalog.isSurfaceAllowed(lowerTooth, ToothSurface.PALATAL)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"11", "16", "24", "31", "36", "47", "51", "64", "73", "85"})
        void allTeethHaveExactlyFiveSurfacesIncludingMesialDistalVestibular(String toothCode) {
            Set<ToothSurface> surfaces = FdiToothCatalog.allowedSurfaces(toothCode);
            assertThat(surfaces).hasSize(5);
            assertThat(surfaces).contains(ToothSurface.MESIAL, ToothSurface.DISTAL, ToothSurface.VESTIBULAR);
        }
    }

    @Nested
    @DisplayName("Catalog Definition & Metadata")
    class CatalogDefinitionTests {

        @Test
        void findDefinitionReturnsCorrectMetadata() {
            var def11 = FdiToothCatalog.findDefinition("11");
            assertThat(def11).isPresent();
            assertThat(def11.get().number()).isEqualTo(11);
            assertThat(def11.get().quadrant()).isEqualTo(1);
            assertThat(def11.get().position()).isEqualTo(1);
            assertThat(def11.get().primaryDentition()).isEqualTo(DentitionType.ADULT);
            assertThat(def11.get().maxillary()).isTrue();
            assertThat(def11.get().anterior()).isTrue();

            var def46 = FdiToothCatalog.findDefinition("46");
            assertThat(def46).isPresent();
            assertThat(def46.get().number()).isEqualTo(46);
            assertThat(def46.get().quadrant()).isEqualTo(4);
            assertThat(def46.get().position()).isEqualTo(6);
            assertThat(def46.get().primaryDentition()).isEqualTo(DentitionType.ADULT);
            assertThat(def46.get().isMandibular()).isTrue();
            assertThat(def46.get().isPosterior()).isTrue();
        }

        @Test
        void nullAndBlankHandling() {
            assertThat(FdiToothCatalog.isValidFdiCode(null)).isFalse();
            assertThat(FdiToothCatalog.isValidFdiCode("")).isFalse();
            assertThat(FdiToothCatalog.isValidFdiCode("   ")).isFalse();
            assertThat(FdiToothCatalog.isValidTooth(null, "11")).isFalse();
            assertThat(FdiToothCatalog.isValidTooth(DentitionType.ADULT, null)).isFalse();
            assertThat(FdiToothCatalog.allowedSurfaces(null)).isEmpty();
            assertThat(FdiToothCatalog.allowedSurfaces("99")).isEmpty();
            assertThat(FdiToothCatalog.isSurfaceAllowed("11", null)).isFalse();
            assertThat(FdiToothCatalog.isSurfaceAllowed(null, ToothSurface.MESIAL)).isFalse();
        }
    }
}
