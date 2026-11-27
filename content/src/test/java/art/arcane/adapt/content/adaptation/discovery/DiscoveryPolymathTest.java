package art.arcane.adapt.content.adaptation.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryPolymathTest {
  @Test
  void totalBonusStacksPerSkillButRespectsTheCap() {
    assertThat(DiscoveryPolymath.totalBonus(4, 0.05D, 1.0D)).isEqualTo(0.2D);
    assertThat(DiscoveryPolymath.totalBonus(100, 0.05D, 1.0D)).isEqualTo(1.0D);
    assertThat(DiscoveryPolymath.totalBonus(0, 0.05D, 1.0D)).isZero();
    assertThat(DiscoveryPolymath.totalBonus(-3, 0.05D, 1.0D)).isZero();
  }

  @Test
  void perSkillBonusGrowsWithLevel() {
    assertThat(DiscoveryPolymath.perSkillBonus(0.2D, 0.015D, 0.045D)).isEqualTo(0.024D, org.assertj.core.data.Offset.offset(1e-9D));
    assertThat(DiscoveryPolymath.perSkillBonus(1.0D, 0.015D, 0.045D))
        .isGreaterThan(DiscoveryPolymath.perSkillBonus(0.0D, 0.015D, 0.045D));
  }
}
