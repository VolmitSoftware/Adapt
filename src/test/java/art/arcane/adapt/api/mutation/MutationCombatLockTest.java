package art.arcane.adapt.api.mutation;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MutationCombatLockTest {
  @Test
  void combatTagsBothParticipantsAndExpiresAtTheExactBoundary() {
    AtomicLong now = new AtomicLong(1_000L);
    MutationCombatLock lock = new MutationCombatLock(10_000L, now::get);
    UUID dealer = UUID.randomUUID();
    UUID receiver = UUID.randomUUID();

    lock.tag(dealer, receiver);
    now.set(10_999L);
    assertThat(lock.isLocked(dealer)).isTrue();
    assertThat(lock.isLocked(receiver)).isTrue();
    now.set(11_000L);
    assertThat(lock.isLocked(dealer)).isFalse();
    assertThat(lock.isLocked(receiver)).isFalse();
  }

  @Test
  void retaggingExtendsButSelfDamageDoesNotTag() {
    AtomicLong now = new AtomicLong(5_000L);
    MutationCombatLock lock = new MutationCombatLock(1_000L, now::get);
    UUID player = UUID.randomUUID();
    UUID other = UUID.randomUUID();

    lock.tag(player, player);
    assertThat(lock.isLocked(player)).isFalse();
    lock.tag(player, other);
    now.addAndGet(500L);
    lock.tag(player);
    now.addAndGet(600L);
    assertThat(lock.isLocked(player)).isTrue();
    lock.clear(player);
    assertThat(lock.isLocked(player)).isFalse();
  }

  @Test
  void activeEntrySaturationNeverExceedsTheHardCap() {
    AtomicLong now = new AtomicLong(10_000L);
    MutationCombatLock lock = new MutationCombatLock(60_000L, now::get);

    for (int index = 0; index < 17_000; index++) {
      lock.tag(new UUID(0L, index + 1L));
    }

    assertThat(lock.trackedEntries()).isEqualTo(16_384);
  }
}
