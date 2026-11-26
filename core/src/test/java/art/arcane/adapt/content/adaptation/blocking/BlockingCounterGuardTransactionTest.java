package art.arcane.adapt.content.adaptation.blocking;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingCounterGuardTransactionTest {
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
  void stackDisplayAlwaysUsesBoundedCounts() {
    assertThat(BlockingCounterGuard.stackFraction(3, 10)).isEqualTo("3/10");
    assertThat(BlockingCounterGuard.stackFraction(-2, 0)).isEqualTo("0/1");
  }

  @Test
  void reflectionKeysAreDirectionalAndConsumedOnce() {
    UUID defenderId = UUID.randomUUID();
    UUID attackerId = UUID.randomUUID();
    Set<BlockingRetaliation.ReflectionKey> pending = ConcurrentHashMap.newKeySet();
    pending.add(new BlockingRetaliation.ReflectionKey(defenderId, attackerId));

    assertThat(pending.remove(
        new BlockingRetaliation.ReflectionKey(defenderId, attackerId)
    )).isTrue();
    assertThat(pending.remove(
        new BlockingRetaliation.ReflectionKey(defenderId, attackerId)
    )).isFalse();
    assertThat(new BlockingRetaliation.ReflectionKey(defenderId, attackerId))
        .isNotEqualTo(new BlockingRetaliation.ReflectionKey(attackerId, defenderId));
  }
}
