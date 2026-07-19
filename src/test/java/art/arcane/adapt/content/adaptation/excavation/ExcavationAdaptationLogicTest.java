package art.arcane.adapt.content.adaptation.excavation;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExcavationAdaptationLogicTest {
  @Test
  void dowsingAcceptsOnlyWaterAndLavaOutsideMinimumDistance() {
    assertThat(ExcavationDowsing.isDowsingTarget(Material.WATER, 25)).isTrue();
    assertThat(ExcavationDowsing.isDowsingTarget(Material.LAVA, 100)).isTrue();
    assertThat(ExcavationDowsing.isDowsingTarget(Material.WATER, 24)).isFalse();
    assertThat(ExcavationDowsing.isDowsingTarget(Material.CAVE_AIR, 100)).isFalse();
    assertThat(ExcavationDowsing.isDowsingTarget(Material.STONE, 100)).isFalse();
  }

  @Test
  void dowsingRevealCountIsAlwaysOneThroughThree() {
    assertThat(ExcavationDowsing.revealCount(0D)).isEqualTo(1);
    assertThat(ExcavationDowsing.revealCount(0.4D)).isEqualTo(2);
    assertThat(ExcavationDowsing.revealCount(1D)).isEqualTo(3);
    assertThat(ExcavationDowsing.revealCount(Double.NaN)).isEqualTo(1);
  }

  @Test
  void hasteLevelsMapToTwentyPercentBreakSpeedPerLevel() {
    assertThat(ExcavationHaste.hasteAmount(1)).isCloseTo(0.20D, within(1e-9D));
    assertThat(ExcavationHaste.hasteAmount(2)).isCloseTo(0.40D, within(1e-9D));
    assertThat(ExcavationHaste.hasteAmount(3)).isCloseTo(0.60D, within(1e-9D));
    assertThat(ExcavationHaste.hasteAmount(0)).isCloseTo(0.20D, within(1e-9D));
  }

  @Test
  void mudlarkWetHasteAmplifierAndScalarMatchPotionTiers() {
    assertThat(ExcavationMudlark.hasteAmplifier(0D, 3D)).isZero();
    assertThat(ExcavationMudlark.hasteAmplifier(0.5D, 3D)).isEqualTo(1);
    assertThat(ExcavationMudlark.hasteAmplifier(1D, 3D)).isEqualTo(2);
    assertThat(ExcavationMudlark.hasteAmplifier(1D, 1D)).isZero();
    assertThat(ExcavationMudlark.hasteAmplifier(-1D, 3D)).isZero();
    assertThat(ExcavationMudlark.hasteScalar(0)).isCloseTo(0.20D, within(1e-9D));
    assertThat(ExcavationMudlark.hasteScalar(1)).isCloseTo(0.40D, within(1e-9D));
    assertThat(ExcavationMudlark.hasteScalar(2)).isCloseTo(0.60D, within(1e-9D));
  }

  @Test
  void earthMoverDamageScalesFromHeldShovelDamage() {
    assertThat(ExcavationEarthMover.DamageScaling.scaledDamage(6.5D, 1.5D)).isEqualTo(9.75D);
    assertThat(ExcavationEarthMover.DamageScaling.scaledDamage(4.5D, 0.75D)).isEqualTo(3.375D);
    assertThat(ExcavationEarthMover.DamageScaling.scaledDamage(-1D, 1D)).isZero();
    assertThat(ExcavationEarthMover.DamageScaling.scaledDamage(5D, Double.NaN)).isZero();
  }

  @Test
  void earthMoverUsesShovelTierDamageAndHalvesEveryCooldownLevel() {
    assertThat(ExcavationEarthMover.shovelDamage(Material.WOODEN_SHOVEL)).isEqualTo(2.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Material.IRON_SHOVEL)).isEqualTo(4.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Material.NETHERITE_SHOVEL)).isEqualTo(6.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Material.DIAMOND_PICKAXE)).isZero();
    assertThat(ExcavationEarthMover.scaledCooldown(16_000D, 8_000D, 0D, 0.5D)).isEqualTo(8_000L);
    assertThat(ExcavationEarthMover.scaledCooldown(16_000D, 8_000D, 1D, 0.5D)).isEqualTo(4_000L);
  }

  @Test
  void seismicLineLengthUsesSegmentCountAndSafeSpacing() {
    assertThat(ExcavationSeismicPing.lineLength(10, 0.55D)).isEqualTo(5.5F);
    assertThat(ExcavationSeismicPing.lineLength(1, 0D)).isEqualTo(0.2F);
  }

  @Test
  void spelunkerMarkersUseOreSpecificGlowColors() {
    assertThat(ExcavationSpelunker.markerColor(Material.DIAMOND_ORE))
        .isEqualTo(Color.fromRGB(90, 230, 235));
    assertThat(ExcavationSpelunker.markerColor(Material.REDSTONE_ORE))
        .isEqualTo(Color.fromRGB(255, 60, 60));
    assertThat(ExcavationSpelunker.markerColor(Material.COAL_ORE)).isEqualTo(Color.WHITE);
  }

  @Test
  void temporaryDisplaysRetireThroughEntityLifecycleEvents() throws ReflectiveOperationException {
    assertRetirementHandler(ExcavationSpelunker.class);
    assertRetirementHandler(ExcavationSeismicPing.class);
  }

  private static void assertRetirementHandler(Class<?> adaptationType) throws ReflectiveOperationException {
    Method handler = adaptationType.getDeclaredMethod("on", EntityRemoveEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
  }
}
