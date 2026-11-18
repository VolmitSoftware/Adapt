package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GolemRunicAugmentsLatticeTest {
  private static final GolemRunicAugments.Config DEFAULTS = new GolemRunicAugments.Config();

  @Test
  void theLatticeUsesTheRunicAugmentsValuesForEachTier() {
    GolemRunicAugments.Lattice lattice = GolemRunicAugments.lattice(DEFAULTS,
        Map.of(GolemRuneType.SWIFTNESS, 1, GolemRuneType.MIGHT, 2, GolemRuneType.BULWARK, 3, GolemRuneType.EMBER, 2));

    assertThat(lattice.stats()).containsExactlyInAnyOrder(
        GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, 0.10D),
        GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 4.0D),
        GolemStatDelta.add(GolemStat.ARMOR, 12.0D),
        GolemStatDelta.add(GolemStat.ARMOR_TOUGHNESS, 4.0D),
        GolemStatDelta.add(GolemStat.KNOCKBACK_RESISTANCE, 0.5D));
    assertThat(lattice.igniteTicks()).isEqualTo(80);
    assertThat(lattice.fireImmune()).isTrue();
  }

  @Test
  void aTierOneEmberIgnitesWithoutFireImmunity() {
    GolemRunicAugments.Lattice lattice = GolemRunicAugments.lattice(DEFAULTS, Map.of(GolemRuneType.EMBER, 1));

    assertThat(lattice.stats()).isEmpty();
    assertThat(lattice.igniteTicks()).isEqualTo(40);
    assertThat(lattice.fireImmune()).isFalse();
  }

  @Test
  void anEmptyLatticeDoesNothing() {
    GolemRunicAugments.Lattice lattice = GolemRunicAugments.lattice(DEFAULTS, Map.of());

    assertThat(lattice.stats()).isEmpty();
    assertThat(lattice.igniteTicks()).isZero();
    assertThat(lattice.fireImmune()).isFalse();
  }

  @Test
  void theMaxTierFollowsTheRunicAugmentsLevel() {
    assertThat(GolemRunicAugments.maxTier(DEFAULTS, 0)).isZero();
    assertThat(GolemRunicAugments.maxTier(DEFAULTS, 1)).isEqualTo(1);
    assertThat(GolemRunicAugments.maxTier(DEFAULTS, 3)).isEqualTo(2);
    assertThat(GolemRunicAugments.maxTier(DEFAULTS, 5)).isEqualTo(3);
  }
}
