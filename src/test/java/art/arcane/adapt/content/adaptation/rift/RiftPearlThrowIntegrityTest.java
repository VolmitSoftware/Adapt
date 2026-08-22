package art.arcane.adapt.content.adaptation.rift;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.EnderPearl;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiftPearlThrowIntegrityTest {
  private static final Path TAGLOCK_SOURCE = Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftEnderTaglock.java");
  private static final Path REBOUND_SOURCE = Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftPearlRebound.java");
  private static final NamespacedKey REBOUND_LEVEL = new NamespacedKey("adapt", "rift_pearl_rebound_level");
  private static final NamespacedKey REBOUNDED = new NamespacedKey("adapt", "rift_pearl_rebounded");
  private static final NamespacedKey TAGLOCK_TARGET = new NamespacedKey("adapt", RiftEnderTaglock.TARGET_KEY_NAME);

  @Test
  void pearlWithoutAnyStoredKeysIsUnclaimed() {
    assertThat(RiftPearls.isUnclaimedPearl(pearlWithKeys())).isTrue();
  }

  @Test
  void pearlCarryingOnlyItsOwnKeysStaysUnclaimed() {
    EnderPearl pearl = pearlWithKeys(REBOUND_LEVEL, REBOUNDED);

    assertThat(RiftPearls.isUnclaimedPearl(pearl, REBOUND_LEVEL, REBOUNDED)).isTrue();
  }

  @Test
  void pearlClaimedByAnotherAdaptationIsNotUnclaimed() {
    EnderPearl pearl = pearlWithKeys(REBOUND_LEVEL, TAGLOCK_TARGET);

    assertThat(RiftPearls.isUnclaimedPearl(pearl, REBOUND_LEVEL, REBOUNDED)).isFalse();
  }

  @Test
  void missingPearlAndMissingContainerAreNeverUnclaimed() {
    assertThat(RiftPearls.isUnclaimedPearl(null)).isFalse();
    assertThat(RiftPearls.isUnclaimedContainer(null)).isFalse();
  }

  @Test
  void reboundOnlyBouncesUnclaimedPearlsThatHaveNotBouncedYet() {
    assertThat(RiftPearlRebound.shouldRebound(2, false, true)).isTrue();
    assertThat(RiftPearlRebound.shouldRebound(null, false, true)).isFalse();
    assertThat(RiftPearlRebound.shouldRebound(0, false, true)).isFalse();
    assertThat(RiftPearlRebound.shouldRebound(2, true, true)).isFalse();
  }

  @Test
  void reboundRefusesPearlsAnotherAdaptationClaimedAfterLaunch() {
    assertThat(RiftPearlRebound.shouldRebound(2, false, false)).isFalse();
  }

  @Test
  void taglockSuppressesTheThrowerTeleportOnEveryImpactThatVanillaStillResolves() {
    assertThat(RiftEnderTaglock.vanillaPearlTeleportStillRuns(false, true)).isTrue();
    assertThat(RiftEnderTaglock.vanillaPearlTeleportStillRuns(false, false)).isTrue();
    assertThat(RiftEnderTaglock.vanillaPearlTeleportStillRuns(true, true)).isTrue();
    assertThat(RiftEnderTaglock.vanillaPearlTeleportStillRuns(true, false)).isFalse();
  }

  @Test
  void taglockSuppressionOnlyAppliesWhileItIsArmed() {
    assertThat(RiftEnderTaglock.shouldSuppressPearlTeleport(1_000L, 999L)).isTrue();
    assertThat(RiftEnderTaglock.shouldSuppressPearlTeleport(1_000L, 1_000L)).isFalse();
    assertThat(RiftEnderTaglock.shouldSuppressPearlTeleport(1_000L, 1_001L)).isFalse();
    assertThat(RiftEnderTaglock.shouldSuppressPearlTeleport(null, 0L)).isFalse();
  }

  @Test
  void taglockStampsItsTargetBeforeTheProjectileSpawns() throws IOException {
    String source = Files.readString(TAGLOCK_SOURCE);
    int launchStart = source.indexOf("p.launchProjectile(");
    int launchEnd = source.indexOf(");", source.indexOf("(EnderPearl pearl) ->", launchStart));
    String launch = source.substring(launchStart, launchEnd);

    assertThat(launch).contains("EnderPearl.class", "targetKey", "PersistentDataType.STRING");
    assertThat(source).doesNotContain("EnderPearl pearl = p.launchProjectile");
  }

  @Test
  void taglockArmsTeleportSuppressionOnImpactRatherThanOnLaunch() throws IOException {
    String source = Files.readString(TAGLOCK_SOURCE);
    int interactStart = source.indexOf("public void on(PlayerInteractEvent e)");
    int interactEnd = source.indexOf("public void on(PlayerTeleportEvent e)", interactStart);
    String interact = source.substring(interactStart, interactEnd);
    int hitStart = source.indexOf("public void on(ProjectileHitEvent e)");
    int hitEnd = source.indexOf("public void on(PlayerQuitEvent e)", hitStart);
    String hit = source.substring(hitStart, hitEnd);
    String hitAnnotations = source.substring(source.lastIndexOf("@ReceiveCancelledEvents", hitStart), hitStart);

    assertThat(interact).doesNotContain("suppressPearlTeleportUntil");
    assertThat(hit).contains("vanillaPearlTeleportStillRuns", "suppressPearlTeleportUntil.put");
    assertThat(hitAnnotations)
        .contains("EventPriority.MONITOR")
        .doesNotContain("ignoreCancelled");
    assertThat(source).doesNotContain("taglockPearlsInFlight", "EntityRemoveEvent");
  }

  @Test
  void reboundConsultsThePearlClaimAtLaunchAndOnImpact() throws IOException {
    String source = Files.readString(REBOUND_SOURCE);
    int launchStart = source.indexOf("public void on(ProjectileLaunchEvent e)");
    int hitStart = source.indexOf("public void on(ProjectileHitEvent e)");
    String launch = source.substring(launchStart, hitStart);
    String hit = source.substring(hitStart, source.indexOf("public void on(EntityDamageEvent e)", hitStart));

    assertThat(launch).contains("RiftPearls.isUnclaimedPearl(pearl, reboundLevelKey, reboundedKey)");
    assertThat(hit).contains("shouldRebound(", "RiftPearls.isUnclaimedPearl(pearl, reboundLevelKey, reboundedKey)");
    assertThat(source).doesNotContain("getPersistentDataContainer().getKeys().isEmpty()");
  }

  private EnderPearl pearlWithKeys(NamespacedKey... keys) {
    EnderPearl pearl = mock(EnderPearl.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(pearl.getPersistentDataContainer()).thenReturn(data);
    when(data.getKeys()).thenReturn(Set.of(keys));
    return pearl;
  }
}
