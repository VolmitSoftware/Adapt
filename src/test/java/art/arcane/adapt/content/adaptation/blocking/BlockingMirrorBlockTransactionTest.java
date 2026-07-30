package art.arcane.adapt.content.adaptation.blocking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingMirrorBlockTransactionTest {
  private static final Path SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/blocking/BlockingMirrorBlock.java"
  );

  @Test
  void onlyAConfirmedTeleportCanConfigureTheReflection() {
    assertThat(BlockingMirrorBlock.teleportCompleted(true, null)).isTrue();
    assertThat(BlockingMirrorBlock.teleportCompleted(false, null)).isFalse();
    assertThat(BlockingMirrorBlock.teleportCompleted(null, null)).isFalse();
    assertThat(BlockingMirrorBlock.teleportCompleted(
        true,
        new IllegalStateException("teleport failed")
    )).isFalse();
  }

  @Test
  void cooldownAndRewardsLiveBehindTheCompletionCallback() throws Exception {
    String source = Files.readString(SOURCE);
    String eventHandler = method(source, "public void on(EntityDamageByEntityEvent", "private void mirrorFlash");
    String completion = method(source, "private void finishProjectileTeleport", "private boolean isMirrorReady");

    assertThat(eventHandler).contains(
        "if (!reflectProjectile(defender, projectile, level))",
        "e.setCancelled(true)"
    ).doesNotContain(
        "setStorage(defender, \"mirrorBlockNext\"",
        "xp(defender, getConfig().xpOnReflect)",
        "addStat(defender, \"blocking.mirror-block.projectiles-reflected\""
    );
    assertThat(completion).contains(
        "if (!teleportCompleted(success, failure))",
        "projectile.setShooter(defender)",
        "J.runEntity(defender, () ->",
        "private void commitReflection",
        "setStorage(defender, \"mirrorBlockNext\"",
        "xp(defender, getConfig().xpOnReflect)",
        "addStat(defender, \"blocking.mirror-block.projectiles-reflected\""
    );
  }

  private static String method(String source, String startMarker, String endMarker) {
    int start = source.indexOf(startMarker);
    int end = source.indexOf(endMarker, start);
    if (start < 0 || end < 0) {
      throw new IllegalArgumentException(
          "Missing method markers: " + startMarker + ", " + endMarker
      );
    }
    return source.substring(start, end);
  }
}
