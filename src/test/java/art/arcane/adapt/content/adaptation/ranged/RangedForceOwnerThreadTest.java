package art.arcane.adapt.content.adaptation.ranged;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RangedForceOwnerThreadTest {
  private static final Path SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/ranged/RangedForce.java"
  );

  @Test
  void damageHandlerSnapshotsTheTargetAndSchedulesShooterWork() throws Exception {
    String source = Files.readString(SOURCE);
    String handler = method(
        source,
        "public void on(EntityDamageByEntityEvent e)",
        "private ForceHitTarget captureForceHitTarget"
    );

    assertThat(handler)
        .contains(
            "ForceHitTarget snapshot = captureForceHitTarget(target)",
            "J.runEntity(player, () -> rewardForceHitOwned(player, snapshot))"
        )
        .doesNotContain(
            "resolveProjectileContext(e)",
            "player.getLocation()",
            "getActiveLevel(player)",
            "xp(player"
        );
  }

  @Test
  void longRangeThresholdRemainsStrictlyBeyondThirtyBlocks() {
    assertThat(RangedForce.isLongRangeHit(900D)).isFalse();
    assertThat(RangedForce.isLongRangeHit(900.0001D)).isTrue();
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
