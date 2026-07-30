package art.arcane.adapt.content.adaptation.blocking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingCounterGuardTransactionTest {
  private static final Path SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/blocking/BlockingCounterGuard.java"
  );

  @Test
  void reservedCostsCannotBeSpentByAnotherPendingReflection() {
    assertThat(BlockingCounterGuard.availableStacks(5, 2)).isEqualTo(3);
    assertThat(BlockingCounterGuard.availableStacks(2, 2)).isZero();
    assertThat(BlockingCounterGuard.availableStacks(2, 5)).isZero();
    assertThat(BlockingCounterGuard.availableStacks(2, -3)).isEqualTo(2);
  }

  @Test
  void stackSpendUsesTheCurrentDefenderStateAndClampsAtZero() {
    assertThat(BlockingCounterGuard.spentStacks(5, 2)).isEqualTo(3);
    assertThat(BlockingCounterGuard.spentStacks(1, 2)).isZero();
    assertThat(BlockingCounterGuard.spentStacks(4, -2)).isEqualTo(4);
  }

  @Test
  void reflectionKeysAreDirectionalAndConsumedOnce() {
    UUID defenderId = UUID.randomUUID();
    UUID attackerId = UUID.randomUUID();
    Set<BlockingCounterGuard.ReflectionKey> pending = ConcurrentHashMap.newKeySet();
    pending.add(new BlockingCounterGuard.ReflectionKey(defenderId, attackerId));

    assertThat(pending.remove(
        new BlockingCounterGuard.ReflectionKey(defenderId, attackerId)
    )).isTrue();
    assertThat(pending.remove(
        new BlockingCounterGuard.ReflectionKey(defenderId, attackerId)
    )).isFalse();
    assertThat(new BlockingCounterGuard.ReflectionKey(defenderId, attackerId))
        .isNotEqualTo(new BlockingCounterGuard.ReflectionKey(attackerId, defenderId));
  }

  @Test
  void targetPolicyIsRevalidatedOnTheAttackerOwner() throws Exception {
    String source = Files.readString(SOURCE);
    String eventHandler = method(source, "public void on(EntityDamageByEntityEvent", "private void stackGainCue");
    String attackerTask = method(source, "private void applyReflectionOnAttacker",
        "private void completeReflectionOnDefender");

    assertThat(eventHandler).doesNotContain("canDamageTarget(defender, attacker)");
    assertThat(attackerTask).contains(
        "!attacker.isValid() || attacker.isDead()",
        "!canDamageTarget(defender, attacker)",
        "attacker.damage(operation.reflectedDamage(), defender)"
    );
    assertThat(attackerTask.indexOf("!canDamageTarget(defender, attacker)"))
        .isLessThan(attackerTask.indexOf("attacker.damage(operation.reflectedDamage(), defender)"));
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
