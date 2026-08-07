package art.arcane.adapt.api.protection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegionPolicyTest {

  @Test
  @DisplayName("the default policy allows xp at an unmodified rate with no grants")
  void defaultPolicyIsInert() {
    assertThat(RegionPolicy.DEFAULT.xpAllowed()).isTrue();
    assertThat(RegionPolicy.DEFAULT.xpMultiplier()).isEqualTo(1D);
    assertThat(RegionPolicy.DEFAULT.powerBonus()).isZero();
    assertThat(RegionPolicy.DEFAULT.unlockedAdaptations()).isEmpty();
    assertThat(RegionPolicy.DEFAULT.grantsAnyAdaptation()).isFalse();
    assertThat(RegionPolicy.DEFAULT.unlocksEverything()).isFalse();
  }

  @Test
  @DisplayName("the xp multiplier clamps into the supported range")
  void xpMultiplierClamps() {
    assertThat(new RegionPolicy(true, -5D, 0, Set.of()).xpMultiplier()).isEqualTo(0D);
    assertThat(new RegionPolicy(true, 50_000D, 0, Set.of()).xpMultiplier()).isEqualTo(1000D);
    assertThat(new RegionPolicy(true, 2.5D, 0, Set.of()).xpMultiplier()).isEqualTo(2.5D);
  }

  @Test
  @DisplayName("a non-finite xp multiplier falls back to one")
  void nonFiniteXpMultiplierFallsBack() {
    assertThat(new RegionPolicy(true, Double.NaN, 0, Set.of()).xpMultiplier()).isEqualTo(1D);
    assertThat(new RegionPolicy(true, Double.POSITIVE_INFINITY, 0, Set.of()).xpMultiplier()).isEqualTo(1D);
    assertThat(new RegionPolicy(true, Double.NEGATIVE_INFINITY, 0, Set.of()).xpMultiplier()).isEqualTo(1D);
  }

  @Test
  @DisplayName("the power bonus clamps into the supported range")
  void powerBonusClamps() {
    assertThat(new RegionPolicy(true, 1D, Integer.MAX_VALUE, Set.of()).powerBonus())
        .isEqualTo(RegionPolicy.MAX_POWER_BONUS);
    assertThat(new RegionPolicy(true, 1D, Integer.MIN_VALUE, Set.of()).powerBonus())
        .isEqualTo(RegionPolicy.MIN_POWER_BONUS);
    assertThat(new RegionPolicy(true, 1D, 12, Set.of()).powerBonus()).isEqualTo(12);
  }

  @Test
  @DisplayName("unlock ids are trimmed, lowercased and stripped of blanks")
  void unlockIdsAreNormalized() {
    Set<String> raw = new HashSet<>();
    raw.add("  Axe-Shield-Splitter ");
    raw.add("");
    raw.add("   ");
    raw.add(null);
    raw.add("PICKAXE-VEINMINER");

    RegionPolicy policy = new RegionPolicy(true, 1D, 0, raw);

    assertThat(policy.unlockedAdaptations()).containsExactlyInAnyOrder("axe-shield-splitter", "pickaxe-veinminer");
    assertThat(policy.grantsAnyAdaptation()).isTrue();
    assertThat(policy.unlocksEverything()).isFalse();
  }

  @Test
  @DisplayName("the wildcard id marks the policy as unlocking everything")
  void wildcardUnlocksEverything() {
    RegionPolicy policy = new RegionPolicy(true, 1D, 0, Set.of(RegionPolicy.UNLOCK_ALL, "axe-shield-splitter"));

    assertThat(policy.unlocksEverything()).isTrue();
    assertThat(policy.grantsAnyAdaptation()).isTrue();
  }

  @Test
  @DisplayName("a null unlock set resolves to an empty set")
  void nullUnlockSetIsEmpty() {
    assertThat(new RegionPolicy(true, 1D, 0, null).unlockedAdaptations()).isEmpty();
  }
}
