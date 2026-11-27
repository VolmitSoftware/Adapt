package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.integration.HiddenVein;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    assertThat(ExcavationHasteBoost.hasteAmount(1)).isCloseTo(0.20D, within(1e-9D));
    assertThat(ExcavationHasteBoost.hasteAmount(2)).isCloseTo(0.40D, within(1e-9D));
    assertThat(ExcavationHasteBoost.hasteAmount(3)).isCloseTo(0.60D, within(1e-9D));
    assertThat(ExcavationHasteBoost.hasteAmount(0)).isCloseTo(0.20D, within(1e-9D));
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
    assertThat(ExcavationEarthMover.shovelDamage(Id.minecraft("wooden_shovel"))).isEqualTo(2.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Id.minecraft("copper_shovel"))).isEqualTo(3.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Id.minecraft("iron_shovel"))).isEqualTo(4.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Id.minecraft("netherite_shovel"))).isEqualTo(6.5D);
    assertThat(ExcavationEarthMover.shovelDamage(Id.minecraft("diamond_pickaxe"))).isZero();
    assertThat(ExcavationEarthMover.scaledCooldown(16_000D, 8_000D, 0D, 0.5D)).isEqualTo(8_000L);
    assertThat(ExcavationEarthMover.scaledCooldown(16_000D, 8_000D, 1D, 0.5D)).isEqualTo(4_000L);
  }

  @Test
  void earthMoverTargetsEveryHostileEnemyFamily() {
    LivingRef groundedEnemy = mock(LivingRef.class);
    when(groundedEnemy.has(EntityTrait.MONSTER)).thenReturn(true);
    when(groundedEnemy.has(EntityTrait.ENEMY)).thenReturn(true);
    LivingRef flyingEnemy = mock(LivingRef.class);
    when(flyingEnemy.has(EntityTrait.ENEMY)).thenReturn(true);
    LivingRef neutralMob = mock(LivingRef.class);

    assertThat(ExcavationEarthMover.isEarthMoverTarget(groundedEnemy)).isTrue();
    assertThat(ExcavationEarthMover.isEarthMoverTarget(flyingEnemy)).isTrue();
    assertThat(ExcavationEarthMover.isEarthMoverTarget(neutralMob)).isFalse();
  }

  @Test
  void seismicMarkersUseOreSpecificGlowColorsAndTwoSecondDuration() {
    assertThat(ExcavationSeismicPing.oreTint(Id.minecraft("diamond_ore")))
        .isEqualTo(new Rgb(90, 230, 235));
    assertThat(ExcavationSeismicPing.oreTint(Id.minecraft("redstone_ore")))
        .isEqualTo(new Rgb(255, 60, 60));
    assertThat(ExcavationSeismicPing.GLOW_DURATION_TICKS).isEqualTo(40);
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
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(temporaryDirectory).build(), List.of())) {
      ExcavationSeismicPing adaptation = TestContent.adaptation(Id.adapt("excavation"), ExcavationSeismicPing::new);
      Path configPath = ComponentHost.of(adaptation).getConfigFile().toPath();
      Files.createDirectories(configPath.getParent());
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

      assertThat(ComponentHost.of(adaptation).reloadConfigFromDisk(false)).isTrue();

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
  }

  @Test
  void spelunkerMarkersUseOreSpecificGlowColors() {
    assertThat(ExcavationSpelunker.markerColor(Id.minecraft("diamond_ore")))
        .isEqualTo(new Rgb(90, 230, 235));
    assertThat(ExcavationSpelunker.markerColor(Id.minecraft("redstone_ore")))
        .isEqualTo(new Rgb(255, 60, 60));
    assertThat(ExcavationSpelunker.markerColor(Id.minecraft("coal_ore"))).isEqualTo(new Rgb(255, 255, 255));
  }

  @Test
  void spelunkerTemporaryDisplaysRetireThroughEntityLifecycleEvents() throws ReflectiveOperationException {
    assertRetirementHandler(ExcavationSpelunker.class);
  }

  @Test
  void blockDropTransferIgnoresAlreadyClaimedDrops() throws ReflectiveOperationException {
    Method handler = ExcavationDropToInventory.class.getDeclaredMethod("on", BlockDropItemEvent.class);
    Handle annotation = handler.getAnnotation(Handle.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.ignoreCancelled()).isTrue();
    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isFalse();
  }

  @Test
  void burrowObservesProtectionAtMonitorBeforeMutating() throws ReflectiveOperationException {
    Method handler = ExcavationBurrow.class.getDeclaredMethod("on", PlayerInteractEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isFalse();
    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isTrue();
  }

  @Test
  void seismicPingTargetsTheHiddenVeinClosestToTheBlockCenter() {
    WorldRef world = mock(WorldRef.class);
    Loc origin = new Loc(world, 10.5D, 64.5D, 10.5D);
    HiddenVein far = new HiddenVein(new Loc(world, 20, 64, 10), Id.minecraft("gold_ore"));
    HiddenVein near = new HiddenVein(new Loc(world, 12, 63, 10), Id.minecraft("iron_ore"));

    assertThat(ExcavationOreScan.nearestVein(List.of(far, near), origin)).isEqualTo(near);
    assertThat(ExcavationOreScan.nearestVein(List.of(), origin)).isNull();
  }

  @Test
  void excavationPreferenceFiltersMatchMaterialNames() {
    assertThat(ExcavationPreferences.Ores.IRON.allows(Id.minecraft("deepslate_iron_ore"))).isTrue();
    assertThat(ExcavationPreferences.Ores.IRON.allows(Id.minecraft("gold_ore"))).isFalse();
    assertThat(ExcavationPreferences.Ores.NETHERITE.allows(Id.minecraft("ancient_debris"))).isTrue();
    assertThat(ExcavationPreferences.Materials.SOIL.allows(Id.minecraft("rooted_dirt"))).isTrue();
    assertThat(ExcavationPreferences.Materials.SOIL.allows(Id.minecraft("mud"))).isTrue();
    assertThat(ExcavationPreferences.Materials.SAND.allows(Id.minecraft("red_sand"))).isTrue();
    assertThat(ExcavationPreferences.Materials.SAND.allows(Id.minecraft("sandstone"))).isFalse();
    assertThat(ExcavationPreferences.Materials.SNOW.allows(Id.minecraft("powder_snow"))).isTrue();
    assertThat(ExcavationPreferences.Palette.AQUA.color(new Rgb(1, 2, 3))).isEqualTo(new Rgb(0, 255, 255));
    assertThat(ExcavationPreferences.Palette.DEFAULT.color(new Rgb(1, 2, 3))).isEqualTo(new Rgb(1, 2, 3));
  }

  private static void assertRetirementHandler(Class<?> adaptationType) throws ReflectiveOperationException {
    Method handler = adaptationType.getDeclaredMethod("on", EntityRemoveEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
  }
}
