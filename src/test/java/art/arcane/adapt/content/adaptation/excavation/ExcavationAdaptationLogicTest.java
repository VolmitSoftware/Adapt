package art.arcane.adapt.content.adaptation.excavation;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

class ExcavationAdaptationLogicTest {
  @Test
  void omniToolCapacityAppliesToTheCombinedComponents() {
    assertThat(ExcavationOmniTool.fitsCapacity(1, 1, 2)).isTrue();
    assertThat(ExcavationOmniTool.fitsCapacity(2, 2, 4)).isTrue();
    assertThat(ExcavationOmniTool.fitsCapacity(2, 3, 4)).isFalse();
    assertThat(ExcavationOmniTool.fitsCapacity(Integer.MAX_VALUE, 1, Integer.MAX_VALUE)).isFalse();
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
    assertThat(ExcavationEarthMover.shovelDamage(Material.COPPER_SHOVEL)).isEqualTo(3.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Material.IRON_SHOVEL)).isEqualTo(4.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Material.NETHERITE_SHOVEL)).isEqualTo(6.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Material.DIAMOND_PICKAXE)).isZero();
    assertThat(ExcavationEarthMover.scaledCooldown(16_000D, 8_000D, 0D, 0.5D)).isEqualTo(8_000L);
    assertThat(ExcavationEarthMover.scaledCooldown(16_000D, 8_000D, 1D, 0.5D)).isEqualTo(4_000L);
  }

  @Test
  void earthMoverTargetsEveryHostileEnemyFamily() {
    Monster groundedEnemy = mock(Monster.class);
    Ghast flyingEnemy = mock(Ghast.class);
    LivingEntity neutralMob = mock(LivingEntity.class);

    assertThat(ExcavationEarthMover.isEarthMoverTarget(groundedEnemy)).isTrue();
    assertThat(ExcavationEarthMover.isEarthMoverTarget(flyingEnemy)).isTrue();
    assertThat(ExcavationEarthMover.isEarthMoverTarget(neutralMob)).isFalse();
  }

  @Test
  void seismicMarkersUseOreSpecificGlowColorsAndTwoSecondDuration() {
    assertThat(ExcavationSeismicPing.oreTint(Material.DIAMOND_ORE))
        .isEqualTo(Color.fromRGB(90, 230, 235));
    assertThat(ExcavationSeismicPing.oreTint(Material.REDSTONE_ORE))
        .isEqualTo(Color.fromRGB(255, 60, 60));
    assertThat(ExcavationSeismicPing.GLOW_DURATION_TICKS).isEqualTo(40);
    assertThat(new ExcavationSeismicPing().shouldCanonicalizeConfigOnLoad()).isTrue();
  }

  @Test
  void seismicRevealWindowRejectsStaleAndUnregisteredExpiryCallbacks() {
    UUID playerId = UUID.randomUUID();
    UUID staleRevealId = UUID.randomUUID();
    UUID currentRevealId = UUID.randomUUID();
    Map<UUID, UUID> activeWindows = new HashMap<>();
    activeWindows.put(playerId, currentRevealId);

    assertThat(ExcavationSeismicPing.pingInProgress(null, currentRevealId)).isTrue();
    assertThat(ExcavationSeismicPing.pingInProgress(null, null)).isFalse();
    assertThat(ExcavationSeismicPing.retireRevealWindow(
        activeWindows, playerId, staleRevealId, true)).isFalse();
    assertThat(activeWindows).containsEntry(playerId, currentRevealId);
    assertThat(ExcavationSeismicPing.retireRevealWindow(
        activeWindows, playerId, currentRevealId, false)).isFalse();
    assertThat(activeWindows).containsEntry(playerId, currentRevealId);
    assertThat(ExcavationSeismicPing.retireRevealWindow(
        activeWindows, playerId, currentRevealId, true)).isTrue();
    assertThat(activeWindows).doesNotContainKey(playerId);
  }

  @Test
  void seismicConfigCanonicalizationRemovesRetiredDisplayFields(@TempDir Path temporaryDirectory)
      throws IOException {
    Path configPath = temporaryDirectory.resolve("excavation-seismic-ping.toml");
    Files.writeString(configPath, """
        scanRangeBase = 17
        glowDurationTicks = 200
        hintSegmentsBase = 7
        hintSegmentsFactor = 9
        segmentSpacing = 0.55
        lineThickness = 0.12
        lineDurationTicks = 400
        lineViewRange = 2.0
        """);
    TestSeismicPing adaptation = new TestSeismicPing(configPath);

    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();

    String canonical = Files.readString(configPath);
    assertThat(canonical).contains("scanRangeBase = 17");
    assertThat(canonical).doesNotContain(
        "glowDurationTicks",
        "hintSegmentsBase",
        "hintSegmentsFactor",
        "segmentSpacing",
        "lineThickness",
        "lineDurationTicks",
        "lineViewRange"
    );
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
  void spelunkerTemporaryDisplaysRetireThroughEntityLifecycleEvents() throws ReflectiveOperationException {
    assertRetirementHandler(ExcavationSpelunker.class);
  }

  private static void assertRetirementHandler(Class<?> adaptationType) throws ReflectiveOperationException {
    Method handler = adaptationType.getDeclaredMethod("on", EntityRemoveEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
  }

  private static final class TestSeismicPing extends ExcavationSeismicPing {
    private final File configFile;
    private final File legacyConfigFile;

    private TestSeismicPing(Path configPath) {
      configFile = configPath.toFile();
      legacyConfigFile = configPath.resolveSibling("excavation-seismic-ping.json").toFile();
    }

    @Override
    protected File getConfigFile() {
      return configFile;
    }

    @Override
    protected File getLegacyConfigFile() {
      return legacyConfigFile;
    }
  }
}
