package be.kdg.airfreight.cargo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AirportCodeTest {
    @Test
    void normalisesToUpperCase() {
        // otherwise bru would never match flights BRU
        assertThat(new AirportCode(" bru ").value()).isEqualTo("BRU");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "BR", "BRUX", "B1U"})
    void rejectsAnythingButThreeLetters(String code) {
        assertThatIllegalArgumentException().isThrownBy(() -> new AirportCode(code));
    }
}
