package com.cardbilling.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentNumberTest {

    @Test
    @DisplayName("accepts an 11-digit document number")
    void acceptsElevenDigits() {
        assertThat(DocumentNumber.of("10000000042").value()).isEqualTo("10000000042");
    }

    @Test
    @DisplayName("keeps leading zeros - a document number is a string of digits, not a number")
    void keepsLeadingZeros() {
        assertThat(DocumentNumber.of("00000000123").value()).isEqualTo("00000000123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000004", "100000000420", "1000000004a", "100.000.004-2", "           "})
    @DisplayName("rejects anything that is not exactly 11 digits")
    void rejectsMalformed(String malformed) {
        assertThatThrownBy(() -> DocumentNumber.of(malformed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> DocumentNumber.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
