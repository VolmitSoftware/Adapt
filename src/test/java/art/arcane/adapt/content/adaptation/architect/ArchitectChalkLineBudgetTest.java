package art.arcane.adapt.content.adaptation.architect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectChalkLineBudgetTest {
  @Test
  void renderBatchHasAHardOwnerCeiling() {
    assertThat(ArchitectChalkLine.renderBatchEnd(0, 1_000)).isEqualTo(64);
    assertThat(ArchitectChalkLine.renderBatchEnd(960, 1_000)).isEqualTo(1_000);
  }

  @Test
  void visualConfigurationIsClampedToSafeBounds() {
    assertThat(ArchitectChalkLine.clampParticleCount(1_000)).isEqualTo(32);
    assertThat(ArchitectChalkLine.clampParticleCount(-1)).isEqualTo(2);
    assertThat(ArchitectChalkLine.clampLineLength(1_000D)).isEqualTo(64D);
    assertThat(ArchitectChalkLine.renderRangeSquared(1_000D)).isEqualTo(96D * 96D);
  }
}
