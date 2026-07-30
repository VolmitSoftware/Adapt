package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.volmlib.util.localization.TextValue;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiftBlinkVoidSkinTest {
  private static final Path BLINK_SOURCE = Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftBlink.java");
  private static final Path REBOUND_SOURCE = Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftPearlRebound.java");

  @Test
  void blinkUsesVanillaPearlDamageThatFallsByLevel() {
    assertThat(RiftBlink.calculatePearlDamage(1, 5D, 1D, 1D)).isEqualTo(5D);
    assertThat(RiftBlink.calculatePearlDamage(3, 5D, 1D, 1D)).isEqualTo(3D);
    assertThat(RiftBlink.calculatePearlDamage(5, 5D, 1D, 1D)).isEqualTo(1D);
    assertThat(RiftBlink.calculatePearlDamage(20, 5D, 1D, 1D)).isEqualTo(1D);
  }

  @Test
  void blinkReachIgnoresObstaclesOnlyWhilePhasing() {
    assertThat(RiftBlink.blinkReach(true, 20D, 6D)).isEqualTo(20D);
    assertThat(RiftBlink.blinkReach(true, 20D, null)).isEqualTo(20D);
    assertThat(RiftBlink.blinkReach(false, 20D, null)).isEqualTo(20D);
    assertThat(RiftBlink.blinkReach(false, 20D, 6D)).isEqualTo(5.5D);
    assertThat(RiftBlink.blinkReach(false, 20D, 0.2D)).isEqualTo(0D);
  }

  @Test
  void blinkPhasesThroughWallsOnlyWhileSneakingWithTheToggleEnabled() throws IOException {
    String source = Files.readString(BLINK_SOURCE);

    assertThat(source).contains("getConfig().phaseWhileSneaking && p.isSneaking()");
    assertThat(fieldNames(RiftBlink.Config.class)).contains("phaseWhileSneaking");
  }

  @Test
  void blinkHasNoPearlConsumptionAndCompletesOnlySuccessfulAsyncTeleports() throws IOException {
    String source = Files.readString(BLINK_SOURCE);
    String configFields = fieldNames(RiftBlink.Config.class);

    assertThat(source)
        .contains("p.teleportAsync", "Boolean.TRUE.equals(success)", "DamageType.ENDER_PEARL")
        .doesNotContain("consumeBlinkPearl", "pearlConsumeChance", "ItemStack");
    assertThat(source.split("Sound.ENTITY_ENDERMAN_TELEPORT", -1)).hasSizeGreaterThanOrEqualTo(3);
    assertThat(configFields).doesNotContain("pearlConsumeChance");
  }

  @Test
  void canonicalConfigSchemasDiscardRetiredBehaviorFields() {
    assertThat(fieldNames(RiftBlink.Config.class)).doesNotContain("pearlConsumeChance");
    assertThat(fieldNames(RiftPearlRebound.Config.class)).doesNotContain("damageWindowMillis");
    assertThat(fieldNames(RiftVoidSkin.Config.class)).doesNotContain("triggerHealthBase", "triggerHealthPerLevel");
  }

  @Test
  void pearlReboundReducesActualEnderPearlDamage() throws IOException {
    assertThat(RiftPearlRebound.calculateReducedDamage(5D, 0.3D)).isEqualTo(3.5D);
    assertThat(RiftPearlRebound.calculateReducedDamage(5D, 0.9D)).isCloseTo(0.5D, offset(1.0E-9D));

    String source = Files.readString(REBOUND_SOURCE);
    assertThat(source)
        .contains("e.getDamageSource().getDamageType()", "DamageType.ENDER_PEARL", "ProjectileLaunchEvent", "J.runEntity")
        .doesNotContain("DamageCause.FALL", "pearlLandedAt", "damageWindowMillis");
  }

  @Test
  void pearlReboundRewardsOnlyAfterDestinationRegionSpawnCompletes() throws IOException {
    String source = Files.readString(REBOUND_SOURCE);
    int finishStart = source.indexOf("private void finishRebound");
    int completionStart = source.indexOf("private void completeReboundSpawn", finishStart);
    int rewardStart = source.indexOf("private void rewardRebound", completionStart);
    String finishMethod = source.substring(finishStart, completionStart);
    String completionMethod = source.substring(completionStart, rewardStart);

    assertThat(finishMethod).doesNotContain("addStat(", "xp(");
    assertThat(completionMethod)
        .contains("if (!isRuntimeRegistered())", "if (!launchReboundPearl",
            "J.runEntity(p, () -> rewardRebound(p))");
    assertThat(completionMethod.indexOf("launchReboundPearl"))
        .isLessThan(completionMethod.indexOf("J.runEntity"));
    assertThat(source).contains("Rift Pearl Rebound failed to create or configure a pearl");
  }

  @Test
  void pearlReboundDescriptionExplainsItsCompleteBehavior() {
    TextValue value = (TextValue) AdaptMessages.require("rift.pearl_rebound.description").englishValue();

    assertThat(value.template())
        .containsIgnoringCase("plain")
        .containsIgnoringCase("bounces once")
        .containsIgnoringCase("crosshair")
        .containsIgnoringCase("next impact")
        .containsIgnoringCase("damage");
  }

  @Test
  void voidSkinRecognizesOnlyGenuinelyLethalSettledDamage() {
    assertThat(RiftVoidSkin.isLethalDamage(10D, 9.99D)).isFalse();
    assertThat(RiftVoidSkin.isLethalDamage(10D, 10D)).isTrue();
    assertThat(RiftVoidSkin.isLethalDamage(10D, 12D)).isTrue();
    assertThat(RiftVoidSkin.isLethalDamage(10D, Double.NaN)).isFalse();
  }

  @Test
  void voidSkinCancelsLethalDamageOnlyWhenTheEscapeWasScheduled() {
    EntityDamageEvent successful = mock(EntityDamageEvent.class);
    EntityDamageEvent failed = mock(EntityDamageEvent.class);

    assertThat(RiftVoidSkin.cancelLethalDamage(successful, true)).isTrue();
    assertThat(RiftVoidSkin.cancelLethalDamage(failed, false)).isFalse();

    verify(successful).setCancelled(true);
    verify(failed, never()).setCancelled(true);
  }

  @Test
  void voidSkinRequiresADeferredProviderReservationToSettle() {
    assertThat(RiftVoidSkin.acceptSettlement(true, false, false)).isTrue();
    assertThat(RiftVoidSkin.acceptSettlement(false, false, false)).isTrue();
    assertThat(RiftVoidSkin.acceptSettlement(false, true, true)).isTrue();
    assertThat(RiftVoidSkin.acceptSettlement(false, true, false)).isFalse();
  }

  @Test
  void voidSkinFallsBackToTheCurrentWorldSpawnWithoutBlockReads() {
    World currentWorld = mock(World.class);
    World otherWorld = mock(World.class);
    Location spawn = new Location(currentWorld, 10.5D, 80D, -4.5D);
    when(currentWorld.getSpawnLocation()).thenReturn(spawn);

    Location fallback = RiftVoidSkin.worldSpawnFallback(currentWorld);

    assertThat(fallback).isEqualTo(spawn).isNotSameAs(spawn);

    Location mismatched = new Location(otherWorld, 0D, 64D, 0D);
    when(currentWorld.getSpawnLocation()).thenReturn(mismatched);
    assertThat(RiftVoidSkin.worldSpawnFallback(currentWorld)).isNull();
    assertThat(RiftVoidSkin.worldSpawnFallback(null)).isNull();
  }

  private static String fieldNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .map(Field::getName)
        .reduce("", (left, right) -> left + " " + right);
  }
}
