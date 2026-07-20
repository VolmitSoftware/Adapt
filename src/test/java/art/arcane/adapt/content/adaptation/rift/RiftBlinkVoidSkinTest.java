package art.arcane.adapt.content.adaptation.rift;

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
  private static final Path ENGLISH_LOCALE = Path.of("src/main/resources/en_US.toml");

  @Test
  void blinkUsesVanillaPearlDamageThatFallsByLevel() {
    assertThat(RiftBlink.calculatePearlDamage(1, 5D, 1D, 1D)).isEqualTo(5D);
    assertThat(RiftBlink.calculatePearlDamage(3, 5D, 1D, 1D)).isEqualTo(3D);
    assertThat(RiftBlink.calculatePearlDamage(5, 5D, 1D, 1D)).isEqualTo(1D);
    assertThat(RiftBlink.calculatePearlDamage(20, 5D, 1D, 1D)).isEqualTo(1D);
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
  void pearlReboundDescriptionExplainsItsCompleteBehavior() throws IOException {
    String section = localeSection(Files.readString(ENGLISH_LOCALE), "rift.pearl_rebound");

    assertThat(section)
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

  private static String localeSection(String locale, String key) {
    String header = "[" + key + "]";
    int start = locale.indexOf(header);
    if (start < 0) {
      throw new IllegalArgumentException("Missing locale section: " + key);
    }
    int end = locale.indexOf("\n[", start + header.length());
    return end < 0 ? locale.substring(start) : locale.substring(start, end);
  }

  private static String fieldNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .map(Field::getName)
        .reduce("", (left, right) -> left + " " + right);
  }
}
