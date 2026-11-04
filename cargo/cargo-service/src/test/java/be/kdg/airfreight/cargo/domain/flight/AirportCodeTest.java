package be.kdg.airfreight.cargo.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AirportCodeTest {
    @Test
    void normalisesToUpperCase() {
        // otherwise bru would never match flights BRU
        assertThat(new AirportCode(" bru ").value()).isEqualTo("BRU");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "BR", "BRUX", "B1U"})
    void rejectsAnythingButThreeLetters(String code) {
        assertThatExceptionOfType(InvalidCargoValueException.class)
                .isThrownBy(() -> new AirportCode(code));
    }
}
