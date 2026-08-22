package art.arcane.adapt.content.adaptation.ranged;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RangedProjectileOwnershipTest {
  private static final Path FLOATERS_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/ranged/RangedFloaters.java");
  private static final Path PINNING_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/ranged/RangedPinningShot.java");

  @Test
  void floatersUsesLaunchAuthorizationAndOwnerRewardHandoff() throws Exception {
    assertOwnershipFlow(Files.readString(FLOATERS_SOURCE).replace("\r\n", "\n"), "ranged.floaters.targets-levitated");
  }

  @Test
  void pinningShotUsesLaunchAuthorizationAndOwnerRewardHandoff() throws Exception {
    assertOwnershipFlow(Files.readString(PINNING_SOURCE).replace("\r\n", "\n"), "ranged.pinning-shot.targets-pinned");
  }

  private static void assertOwnershipFlow(String source, String statKey) {
    String launch = method(source, "public void on(ProjectileLaunchEvent e)");
    String damage = method(source, "public void on(EntityDamageByEntityEvent e)");
    String reward = method(source, "private void rewardProcOwned(Player owner)");

    assertThat(source).contains(
        "@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)\n"
            + "  public void on(EntityDamageByEntityEvent e)");
    assertThat(launch)
        .contains(
            "RangedHeartseeker.isSeekingProjectile(projectile)",
            "getActiveLevel(player)",
            "data.set(shotLevelKey, PersistentDataType.INTEGER, level)",
            "data.set(shotOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString())");
    assertThat(damage)
        .contains(
            "RangedHeartseeker.isSeekingProjectile(e.getDamager())",
            "readShotAuthorization(projectile)",
            "isProtectedTarget(authorization.ownerId(), target)",
            "rewardProc(authorization.ownerId())")
        .doesNotContain(
            "projectile.getShooter()",
            "resolveProjectileContext",
            "getActiveLevel(",
            "addStat(",
            "xp(");
    assertThat(reward)
        .contains(
            "owner.isOnline()",
            "isRuntimeRegistered()",
            "getActiveLevel(owner)",
            "addStat(owner, \"" + statKey + "\", 1)",
            "xp(owner");
  }

  private static String method(String source, String signature) {
    int start = source.indexOf(signature);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int nextMethod = source.indexOf("\n  private ", start);
    assertThat(nextMethod).isGreaterThan(start);
    return source.substring(start, nextMethod);
  }
}
