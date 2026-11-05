package be.kdg.airfreight.cargo.domain.cargoplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WeightTest {
    @Test
    void kilogramsStayAsTheyAre() {
        assertThat(new Weight(new BigDecimal("1250.5"), WeightUnit.KG).inKilograms())
                .isEqualByComparingTo("1250.5");
    }

    @Test
    void poundsUseTheExactInternationalFactor() {
        assertThat(new Weight(new BigDecimal("1000"), WeightUnit.LB).inKilograms())
                .isEqualByComparingTo("453.59237");
    }
}
