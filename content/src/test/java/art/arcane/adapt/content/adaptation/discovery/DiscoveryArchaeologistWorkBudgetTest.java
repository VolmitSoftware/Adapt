package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.platform.value.Id;
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
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(Id.minecraft("suspicious_sand"))).isTrue();
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(Id.minecraft("suspicious_gravel"))).isTrue();
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(Id.minecraft("sand"))).isFalse();
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(Id.minecraft("gravel"))).isFalse();
    assertThat(DiscoveryArchaeologist.isSuspiciousBlock(null)).isFalse();
  }
}
