package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.content.adaptation.rift.RiftEnderTaglock;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the isolation contract: an Adapt item on cooldown must gray out and
 * block only itself, never the vanilla item it is built from.
 */
class ItemCooldownWiringTest {
  private static final Path TAGLOCK =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftEnderTaglock.java");
  private static final Path GATE =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftGate.java");
  private static final Path WIRELESS_REDSTONE =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/architect/ArchitectWirelessRedstone.java");
  private static final Path TIME_BOMB =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/chronos/ChronosTimeBomb.java");

  private static final List<NamespacedKey> DECLARED_GROUPS = List.of(
      BoundEyeOfEnder.COOLDOWN_GROUP,
      BoundRedstoneTorch.COOLDOWN_GROUP,
      ChronoTimeBombItem.COOLDOWN_GROUP,
      RiftEnderTaglock.COOLDOWN_GROUP
  );

  @Test
  void everyCustomItemCooldownGroupLivesInTheAdaptNamespaceNotMinecraft() {
    for (NamespacedKey group : DECLARED_GROUPS) {
      assertThat(group.getNamespace())
          .as(group + " must not collide with a vanilla item cooldown group")
          .isEqualTo(ItemCooldowns.NAMESPACE)
          .isNotEqualTo(NamespacedKey.MINECRAFT);
    }
  }

  @Test
  void noTwoCustomItemsShareACooldownGroup() {
    Set<NamespacedKey> unique = new LinkedHashSet<>(DECLARED_GROUPS);

    assertThat(unique).hasSameSizeAs(DECLARED_GROUPS);
  }

  @Test
  void theTaglockGroupIsNotThePlainEnderPearlGroup() {
    assertThat(RiftEnderTaglock.COOLDOWN_GROUP.toString()).isEqualTo("adapt:item_rift_ender_taglock");
    assertThat(RiftEnderTaglock.COOLDOWN_GROUP).isNotEqualTo(NamespacedKey.minecraft("ender_pearl"));
  }

  @Test
  void theBoundEyeGroupIsNotThePlainEnderEyeGroup() {
    assertThat(BoundEyeOfEnder.COOLDOWN_GROUP).isNotEqualTo(NamespacedKey.minecraft("ender_eye"));
  }

  @Test
  void theBoundTorchGroupIsNotThePlainRedstoneTorchGroup() {
    assertThat(BoundRedstoneTorch.COOLDOWN_GROUP).isNotEqualTo(NamespacedKey.minecraft("redstone_torch"));
  }

  @Test
  void theTaglockNoLongerPutsItsCooldownOnEveryEnderPearl() throws IOException {
    String source = Files.readString(TAGLOCK);

    assertThat(source).doesNotContain("setCooldown(Material.ENDER_PEARL");
    assertThat(source).doesNotContain("hasCooldown(Material.ENDER_PEARL");
    assertThat(source).contains("throwCooldown.isReady(p, throwCooldownMillis)");
    assertThat(source).contains("throwCooldown.mark(p, throwCooldownMillis)");
  }

  @Test
  void theGateNoLongerPutsItsCooldownOnEveryEnderEye() throws IOException {
    String source = Files.readString(GATE);

    assertThat(source).doesNotContain("setCooldown(Material.ENDER_EYE");
    assertThat(source).doesNotContain("hasCooldown(Material.ENDER_EYE");
    assertThat(source).contains("ItemCooldowns.forGroup(BoundEyeOfEnder.COOLDOWN_GROUP)");
  }

  @Test
  void theRedstoneRemoteNoLongerPutsItsCooldownOnEveryRedstoneTorch() throws IOException {
    String source = Files.readString(WIRELESS_REDSTONE);

    assertThat(source).doesNotContain("setCooldown(Material.REDSTONE_TORCH");
    assertThat(source).contains("ItemCooldowns.forGroup(BoundRedstoneTorch.COOLDOWN_GROUP)");
  }

  @Test
  void theTaglockGateRunsBeforeThePearlIsSpentOrLaunched() throws IOException {
    String source = Files.readString(TAGLOCK);

    int gateIndex = source.indexOf("if (!throwCooldown.isReady(p, throwCooldownMillis))");
    int spendIndex = source.indexOf("if (!decrementTaggedPearl(p, slot, hand))");
    int launchIndex = source.indexOf("p.launchProjectile(");

    assertThat(gateIndex).isGreaterThan(0);
    assertThat(spendIndex).isGreaterThan(gateIndex);
    assertThat(launchIndex).isGreaterThan(spendIndex);
  }

  @Test
  void theTimeBombPushesItsSweepAfterVanillaFinishesTheThrow() throws IOException {
    String source = Files.readString(TIME_BOMB);

    assertThat(source).contains("ItemCooldowns.pushGroup(p, ChronoTimeBombItem.COOLDOWN_GROUP");
    assertThat(source).contains("J.runEntity(p, () -> {");
  }

  @Test
  void theTimeBombStillCancelsTheThrowWhileItIsCoolingDown() throws IOException {
    String source = Files.readString(TIME_BOMB);

    int gateIndex = source.indexOf("long cooldown = cooldowns.getOrDefault(p.getUniqueId(), 0L);");
    int cancelIndex = source.indexOf("e.setCancelled(true);", gateIndex);
    int armIndex = source.indexOf("activeBombProjectiles.put(potion.getUniqueId()");

    assertThat(gateIndex).isGreaterThan(0);
    assertThat(cancelIndex).isGreaterThan(gateIndex);
    assertThat(armIndex).isGreaterThan(cancelIndex);
  }
}
