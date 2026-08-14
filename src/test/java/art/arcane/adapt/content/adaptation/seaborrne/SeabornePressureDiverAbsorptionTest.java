package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeabornePressureDiverAbsorptionTest {
  @Test
  void absorptionScalesFromTwoToSixHeartsAcrossLevels() {
    assertThat(SeabornePressureDiver.absorptionHealth(4D, 8D, 1, 4)).isEqualTo(4D);
    assertThat(SeabornePressureDiver.absorptionHealth(4D, 8D, 2, 4)).isCloseTo(20D / 3D, offset(1.0E-12D));
    assertThat(SeabornePressureDiver.absorptionHealth(4D, 8D, 4, 4)).isEqualTo(12D);
    assertThat(SeabornePressureDiver.absorptionHealth(4D, 8D, 9, 4)).isEqualTo(12D);
  }

  @Test
  void invalidOrInactiveAbsorptionConfigFailsClosed() {
    assertThat(SeabornePressureDiver.absorptionHealth(4D, 8D, 0, 4)).isZero();
    assertThat(SeabornePressureDiver.absorptionHealth(Double.NaN, 8D, 1, 4)).isZero();
    assertThat(SeabornePressureDiver.absorptionHealth(4D, Double.POSITIVE_INFINITY, 1, 4)).isZero();
    assertThat(SeabornePressureDiver.absorptionHealth(-4D, 0D, 1, 4)).isZero();
  }

  @Test
  void refreshDoesNotRefillConsumedAbsorption() {
    assertThat(SeabornePressureDiver.absorptionFillTarget(1D, 4D, 4D, 4D)).isEqualTo(1D);
    assertThat(SeabornePressureDiver.absorptionFillTarget(1D, 4D, 8D, 8D)).isEqualTo(5D);
    assertThat(SeabornePressureDiver.absorptionFillTarget(0D, 0D, 4D, 4D)).isEqualTo(4D);
    assertThat(SeabornePressureDiver.absorptionFillTarget(3D, 0D, 4D, 5D)).isEqualTo(5D);
    assertThat(SeabornePressureDiver.absorptionFillTarget(8D, 8D, 4D, 4D)).isEqualTo(4D);
  }

  @Test
  void pressureDiverUsesAbsorptionInsteadOfWaterBreathing() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/seaborrne/SeabornePressureDiver.java"));

    assertThat(source)
        .contains("Attributes.MAX_ABSORPTION", "PotionEffectType.RESISTANCE")
        .doesNotContain("PotionEffectType.WATER_BREATHING");
  }

  @Test
  void configExposesAbsorptionHealthKnobs() {
    SeabornePressureDiver.Config config = new SeabornePressureDiver.Config();

    assertThat(config.absorptionHealthBase).isEqualTo(4D);
    assertThat(config.absorptionHealthFactor).isEqualTo(8D);
  }
}
