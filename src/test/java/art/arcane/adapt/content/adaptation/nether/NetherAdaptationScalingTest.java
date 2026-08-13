package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.util.config.TomlCodec;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NetherAdaptationScalingTest {
  @Test
  void soulStriderStrideSpeedRisesWithLevelAndStaysUsableAtLowLevel() {
    double low = NetherSoulStrider.strideSpeed(0.2D, 0.20D, 0.10D);
    double high = NetherSoulStrider.strideSpeed(1.0D, 0.20D, 0.10D);

    assertThat(low).isGreaterThanOrEqualTo(0.20D);
    assertThat(high).isGreaterThan(low);
    assertThat(high).isCloseTo(0.30D, within(1.0E-9D));
  }

  @Test
  void magmaSkinReflectTicksAndBonusDamageScaleUpNeverNegative() {
    int lowTicks = NetherMagmaSkin.reflectFireTicks(0.25D, 40D, 60D);
    int maxTicks = NetherMagmaSkin.reflectFireTicks(1.0D, 40D, 60D);
    assertThat(lowTicks).isPositive();
    assertThat(maxTicks).isGreaterThan(lowTicks);

    double lowDamage = NetherMagmaSkin.bonusDamage(0.25D, 0.5D, 2.5D);
    double maxDamage = NetherMagmaSkin.bonusDamage(1.0D, 0.5D, 2.5D);
    assertThat(lowDamage).isPositive();
    assertThat(maxDamage).isGreaterThan(lowDamage);
    assertThat(maxDamage).isEqualTo(3.0D);
  }

  @Test
  void netherrackMasonHasteTierIsAtLeastOneAndBonusChanceIsCapped() {
    assertThat(NetherNetherrackMason.hasteTier(0.0D, 1D, 1.5D)).isGreaterThanOrEqualTo(1);
    assertThat(NetherNetherrackMason.hasteTier(1.0D, 1D, 1.5D))
        .isGreaterThan(NetherNetherrackMason.hasteTier(0.25D, 1D, 1.5D));

    double capped = NetherNetherrackMason.bonusDropChance(1.0D, 0.08D, 0.35D, 0.4D);
    assertThat(capped).isEqualTo(0.4D);
    assertThat(NetherNetherrackMason.bonusDropChance(0.25D, 0.08D, 0.35D, 0.4D)).isLessThan(capped);
  }

  @Test
  void netherrackMasonBreakSpeedBonusMatchesHasteTierParity() {
    assertThat(NetherNetherrackMason.breakSpeedBonus(1)).isCloseTo(0.2D, within(1.0E-9D));
    assertThat(NetherNetherrackMason.breakSpeedBonus(2)).isCloseTo(0.4D, within(1.0E-9D));
    assertThat(NetherNetherrackMason.breakSpeedBonus(3)).isCloseTo(0.6D, within(1.0E-9D));
    assertThat(NetherNetherrackMason.breakSpeedBonus(0)).isCloseTo(0.2D, within(1.0E-9D));
  }

  @Test
  void striderBondSpeedAmplifierAndSearchRadiusStayWithinBounds() {
    assertThat(NetherStriderBond.striderSpeedAmplifier(0.25D, 0D, 1.5D)).isGreaterThanOrEqualTo(0);
    assertThat(NetherStriderBond.striderSpeedAmplifier(1.0D, 0D, 1.5D))
        .isGreaterThan(NetherStriderBond.striderSpeedAmplifier(0.25D, 0D, 1.5D));

    assertThat(NetherStriderBond.searchRadius(1.0D, 4D, 4D, 8)).isEqualTo(8);
    assertThat(NetherStriderBond.searchRadius(0.0D, 4D, 4D, 8)).isGreaterThanOrEqualTo(1);
  }

  @Test
  void crimsonFeastFoodAndResistTicksAreAlwaysUsableAndScaleUp() {
    assertThat(NetherCrimsonFeast.floraFood(0.25D, 2D, 4D)).isGreaterThanOrEqualTo(1);
    assertThat(NetherCrimsonFeast.floraFood(1.0D, 2D, 4D))
        .isGreaterThan(NetherCrimsonFeast.floraFood(0.25D, 2D, 4D));

    assertThat(NetherCrimsonFeast.resistTicks(0.25D, 60D, 140D)).isGreaterThanOrEqualTo(1);
    assertThat(NetherCrimsonFeast.resistTicks(1.0D, 60D, 140D)).isEqualTo(200);
  }

  @Test
  void crimsonFeastAcceptsVanillaPredictiveAirDenialButRespectsDeniedBlocks() {
    assertThat(NetherCrimsonFeast.blocksFloraEating(false, Event.Result.DENY)).isFalse();
    assertThat(NetherCrimsonFeast.blocksFloraEating(true, Event.Result.DEFAULT)).isFalse();
    assertThat(NetherCrimsonFeast.blocksFloraEating(true, Event.Result.ALLOW)).isFalse();
    assertThat(NetherCrimsonFeast.blocksFloraEating(true, Event.Result.DENY)).isTrue();
  }

  @Test
  void ashwalkerTierCoverageUnlocksProgressively() {
    assertThat(NetherAshwalker.coversCampfire(1, 2)).isFalse();
    assertThat(NetherAshwalker.coversCampfire(2, 2)).isTrue();
    assertThat(NetherAshwalker.coversSoulFire(2, 3)).isFalse();
    assertThat(NetherAshwalker.coversSoulFire(3, 3)).isTrue();
  }

  @Test
  void ashwalkerRecognizesDedicatedCampfireDamage() {
    assertThat(NetherAshwalker.isCampfireDamage(EntityDamageEvent.DamageCause.CAMPFIRE)).isTrue();
    assertThat(NetherAshwalker.isCampfireDamage(EntityDamageEvent.DamageCause.FIRE)).isFalse();
    assertThat(NetherAshwalker.isCampfireDamage(EntityDamageEvent.DamageCause.FIRE_TICK)).isFalse();
  }

  @Test
  void ashwalkerHardCancelsImmuneDamageAndDefaultsToSilentFeedback() {
    EntityDamageEvent event = mock(EntityDamageEvent.class);

    NetherAshwalker.fullyNegateDamage(event);

    verify(event).setDamage(0D);
    verify(event).setCancelled(true);
    assertThat(new NetherAshwalker.Config().immunitySoundVolume).isZero();
    assertThat(TomlCodec.toToml(new NetherAshwalker.Config(), "adaptation:nether-ashwalker"))
        .contains("immunitySoundVolume = 0.0");
    assertThat(NetherAshwalker.immunitySoundVolume(Double.NaN)).isZero();
    assertThat(NetherAshwalker.immunitySoundVolume(-1D)).isZero();
    assertThat(NetherAshwalker.immunitySoundVolume(0.4D)).isCloseTo(0.4F, within(1.0E-6F));
    assertThat(NetherAshwalker.immunitySoundVolume(2D)).isEqualTo(1F);
  }

  @Test
  void witherHarvestBonusAmountsAndSkullChanceStayBoundedAndScale() {
    assertThat(NetherWitherHarvest.bonusAmount(0.25D, 1D, 2D)).isGreaterThanOrEqualTo(1);
    assertThat(NetherWitherHarvest.bonusAmount(1.0D, 1D, 2D))
        .isGreaterThan(NetherWitherHarvest.bonusAmount(0.0D, 1D, 2D));

    double capped = NetherWitherHarvest.skullChance(1.0D, 0.03D, 0.12D, 0.15D);
    assertThat(capped).isEqualTo(0.15D);
    assertThat(NetherWitherHarvest.skullChance(0.0D, 0.03D, 0.12D, 0.15D)).isEqualTo(0.03D);
  }
}
