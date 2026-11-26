package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
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
  private static final Path BOUNCE_SOURCE = Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftPearlBounce.java");

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
  void blinkPhasingHonorsTheServerAndPlayerModes() {
    assertThat(RiftBlink.canPhase(true, RiftBlink.Phasing.SNEAK, true)).isTrue();
    assertThat(RiftBlink.canPhase(true, RiftBlink.Phasing.SNEAK, false)).isFalse();
    assertThat(RiftBlink.canPhase(true, RiftBlink.Phasing.AIM, false)).isTrue();
    assertThat(RiftBlink.canPhase(true, RiftBlink.Phasing.NEVER, true)).isFalse();
    for (RiftBlink.Phasing mode : RiftBlink.Phasing.values()) {
      assertThat(RiftBlink.canPhase(false, mode, true)).isFalse();
    }
  }

  @Test
  void blinkHasNoPearlConsumptionAndCompletesOnlySuccessfulAsyncTeleports() throws IOException {
    String source = Files.readString(BLINK_SOURCE);
    String configFields = fieldNames(RiftBlink.Config.class);

    assertThat(source)
        .contains("p.teleport(operation.requestedDestination(), TeleportCause.PLUGIN)", "Boolean.TRUE.equals(success)",
            "ENDER_PEARL_DAMAGE = Id.minecraft(\"ender_pearl\")")
        .doesNotContain("consumeBlinkPearl", "pearlConsumeChance", "ItemRef");
    assertThat(source.split("Sounds.ENTITY_ENDERMAN_TELEPORT", -1)).hasSizeGreaterThanOrEqualTo(3);
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

    String source = Files.readString(REBOUND_SOURCE) + Files.readString(BOUNCE_SOURCE);
    assertThat(source)
        .contains("e.damageType()", "ENDER_PEARL.equals(e.damageType())", "ProjectileLaunchEvent", "Tasks.runEntity")
        .doesNotContain("DamageCause.FALL", "pearlLandedAt", "damageWindowMillis");
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
    WorldRef currentWorld = mock(WorldRef.class);
    WorldRef otherWorld = mock(WorldRef.class);
    Loc spawn = new Loc(currentWorld, 10.5D, 80D, -4.5D);
    when(currentWorld.spawnLocation()).thenReturn(spawn);

    Loc fallback = RiftVoidSkin.worldSpawnFallback(currentWorld);

    assertThat(fallback).isEqualTo(spawn).isNotSameAs(spawn);

    Loc mismatched = new Loc(otherWorld, 0D, 64D, 0D);
    when(currentWorld.spawnLocation()).thenReturn(mismatched);
    assertThat(RiftVoidSkin.worldSpawnFallback(currentWorld)).isNull();
    assertThat(RiftVoidSkin.worldSpawnFallback(null)).isNull();
  }

  private static String fieldNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .map(Field::getName)
        .reduce("", (left, right) -> left + " " + right);
  }
}
