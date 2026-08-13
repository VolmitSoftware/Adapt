package art.arcane.adapt.content.adaptation.discovery;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryArchaeologistWorkBudgetTest {
  @Test
  void fallbackChecksNeverExceedTheirBudget() {
    assertThat(DiscoveryArchaeologist.workFor(1000, 16)).isEqualTo(16);
    assertThat(DiscoveryArchaeologist.workFor(12, 16)).isEqualTo(12);
    assertThat(DiscoveryArchaeologist.workFor(-1, 16)).isZero();
  }

  @Test
  void onlySuspiciousSandAndGravelAreArchaeologyTargets() {
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(Material.SUSPICIOUS_SAND)).isTrue();
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(Material.SUSPICIOUS_GRAVEL)).isTrue();
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(Material.SAND)).isFalse();
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(Material.GRAVEL)).isFalse();
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(null)).isFalse();
  }
}
