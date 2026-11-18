package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChronosBorrowedTimeDamageTest {
  @TempDir
  Path dataDirectory;

  private TestAdapt adapt;

  @BeforeEach
  void setUp() {
    adapt = TestAdapt.start(TestPlatform.builder(dataDirectory).build(), List.of());
  }

  @AfterEach
  void tearDown() {
    adapt.close();
  }

  @Test
  void paybackDamageCannotDeferItselfAndRestoresNestedState() {
    ChronosBorrowedTime borrowedTime = new ChronosBorrowedTime();

    assertThat(borrowedTime.isApplyingPaybackDamage()).isFalse();
    borrowedTime.withPaybackDamage(() -> {
      assertThat(borrowedTime.isApplyingPaybackDamage()).isTrue();
      borrowedTime.withPaybackDamage(() -> assertThat(borrowedTime.isApplyingPaybackDamage()).isTrue());
      assertThat(borrowedTime.isApplyingPaybackDamage()).isTrue();
    });
    assertThat(borrowedTime.isApplyingPaybackDamage()).isFalse();
  }

  @Test
  void deferredDebtRoundTripsAndOnlyAdvancesOnCommit() {
    Deque<ChronosBorrowedTime.DeferredDamage> debt = new ConcurrentLinkedDeque<>();
    debt.add(new ChronosBorrowedTime.DeferredDamage(2.5D, 2));
    debt.add(new ChronosBorrowedTime.DeferredDamage(0.5D, 1));

    String encoded = ChronosBorrowedTime.encodeDeferredDamage(debt);
    Deque<ChronosBorrowedTime.DeferredDamage> restored =
        ChronosBorrowedTime.decodeDeferredDamage(encoded);

    assertThat(ChronosBorrowedTime.pendingPulseAmount(restored)).isEqualTo(3D);
    assertThat(restored).extracting(ChronosBorrowedTime.DeferredDamage::pulsesRemaining)
        .containsExactly(2, 1);

    ChronosBorrowedTime.commitDebtPulse(restored);

    assertThat(ChronosBorrowedTime.pendingPulseAmount(restored)).isEqualTo(2.5D);
    assertThat(restored).extracting(ChronosBorrowedTime.DeferredDamage::pulsesRemaining)
        .containsExactly(1);

    ChronosBorrowedTime.commitDebtPulse(restored);
    assertThat(restored).isEmpty();
  }

  @Test
  void malformedPersistentDebtIsRejected() {
    assertThatThrownBy(() -> ChronosBorrowedTime.decodeDeferredDamage("NaN,4"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ChronosBorrowedTime.decodeDeferredDamage("2.0,0"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ChronosBorrowedTime.decodeDeferredDamage("broken"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cancelledHitCreatesNoDebtOrDeferredDamageCredit() throws Exception {
    QuietBorrowedTime borrowedTime = new QuietBorrowedTime();
    PlayerRef player = damagePlayer();
    EntityDamageEvent hit = damage(player, 100D, 8D);

    borrowedTime.on(hit);
    hit.setCancelled(true);
    borrowedTime.finishDamage(hit);

    assertThat(deferred(borrowedTime)).isEmpty();
    assertThat(borrowedTime.damageCredit).isZero();
    verify(player.data(), never()).set(any(), any());
  }

  @Test
  void acceptedHitOwesTheHealthDamageActuallyDeferred() throws Exception {
    QuietBorrowedTime borrowedTime = new QuietBorrowedTime();
    PlayerRef player = damagePlayer();
    EntityDamageEvent hit = damage(player, 10D, 8D);

    borrowedTime.on(hit);
    assertThat(hit.finalDamage()).isZero();
    borrowedTime.finishDamage(hit);
    borrowedTime.finishDamage(hit);

    assertThat(deferred(borrowedTime).get(player.uuid())).hasSize(1);
    assertThat(ChronosBorrowedTime.pendingPulseAmount(deferred(borrowedTime).get(player.uuid())))
        .isEqualTo(0.2D);
    assertThat(borrowedTime.damageCredit).isEqualTo(2D);
  }

  @Test
  void nestedHitsCommitIndependentlyAndCancellationKeepsExistingDebt() throws Exception {
    QuietBorrowedTime borrowedTime = new QuietBorrowedTime();
    PlayerRef player = damagePlayer();
    EntityDamageEvent outer = damage(player, 10D, 0D);
    EntityDamageEvent inner = damage(player, 20D, 0D);

    borrowedTime.on(outer);
    borrowedTime.on(inner);
    borrowedTime.finishDamage(inner);
    outer.setCancelled(true);
    borrowedTime.finishDamage(outer);

    assertThat(deferred(borrowedTime).get(player.uuid())).hasSize(1);
    assertThat(borrowedTime.damageCredit).isEqualTo(8D);
  }

  @Test
  void monitorCopyOfAHitPairsWithItsOwnHighestCopyByDispatchId() throws Exception {
    QuietBorrowedTime borrowedTime = new QuietBorrowedTime();
    PlayerRef player = damagePlayer();
    EntityDamageEvent outerHighest = damage(player, 10D, 0D);
    EntityDamageEvent innerHighest = damage(player, 20D, 0D);

    borrowedTime.on(outerHighest);
    borrowedTime.on(innerHighest);
    EntityDamageEvent outerMonitor = damage(player, 6D, 0D);
    outerMonitor.setDispatchId(outerHighest.dispatchId());
    borrowedTime.finishDamage(outerMonitor);

    assertThat(borrowedTime.damageCredit).isEqualTo(4D);

    EntityDamageEvent unrelatedMonitor = damage(player, 12D, 0D);
    borrowedTime.finishDamage(unrelatedMonitor);
    assertThat(borrowedTime.damageCredit).isEqualTo(4D);

    EntityDamageEvent innerMonitor = damage(player, 12D, 0D);
    innerMonitor.setDispatchId(innerHighest.dispatchId());
    borrowedTime.finishDamage(innerMonitor);
    assertThat(borrowedTime.damageCredit).isEqualTo(12D);
    assertThat(deferred(borrowedTime).get(player.uuid())).hasSize(2);
  }

  @Test
  void persistenceFailureRetainsDebtAlreadyGrantedByTheHit() throws Exception {
    QuietBorrowedTime borrowedTime = new QuietBorrowedTime();
    PlayerRef player = damagePlayer();
    DataHolder data = player.data();
    doThrow(new IllegalStateException("Storage unavailable"))
        .when(data).set(any(), any());
    EntityDamageEvent hit = damage(player, 10D, 0D);

    borrowedTime.on(hit);
    borrowedTime.finishDamage(hit);

    assertThat(deferred(borrowedTime).get(player.uuid())).hasSize(1);
    assertThat(borrowedTime.damageCredit).isEqualTo(4D);
  }

  @Test
  void invulnerabilityPreservesDebtUntilEveryPulseCanBeApplied() throws Exception {
    QuietBorrowedTime borrowedTime = new QuietBorrowedTime();
    PlayerRef player = mock(PlayerRef.class);
    DataHolder data = mock(DataHolder.class);
    UUID id = UUID.randomUUID();
    when(player.uuid()).thenReturn(id);
    when(player.isOnline()).thenReturn(true);
    when(player.health()).thenReturn(16.4D);
    when(player.data()).thenReturn(data);
    when(player.maxNoDamageTicks()).thenReturn(20);
    when(player.noDamageTicks()).thenReturn(11);
    Deque<ChronosBorrowedTime.DeferredDamage> queue = new ConcurrentLinkedDeque<>();
    queue.add(new ChronosBorrowedTime.DeferredDamage(0.24D, 10));
    deferred(borrowedTime).put(id, queue);
    Method settle = ChronosBorrowedTime.class.getDeclaredMethod("settleDeferredDamage", PlayerRef.class, Deque.class);
    settle.setAccessible(true);

    settle.invoke(borrowedTime, player, queue);

    assertThat(ChronosBorrowedTime.encodeDeferredDamage(queue)).isEqualTo("0.24,10");
    assertThat(deferred(borrowedTime)).containsEntry(id, queue);
    assertThat(borrowedTime.paybacks).isEmpty();
    verify(data, never()).set(any(), any());
    verify(data, never()).remove(any(DataKey.class));

    when(player.noDamageTicks()).thenReturn(10);
    settle.invoke(borrowedTime, player, queue);
    verify(data).set(any(), eq("0.24,9"));
    for (int pulse = 1; pulse < 10; pulse++) {
      settle.invoke(borrowedTime, player, queue);
    }

    assertThat(borrowedTime.paybacks).hasSize(10).allMatch(amount -> amount == 0.24D);
    assertThat(queue).isEmpty();
    assertThat(deferred(borrowedTime)).doesNotContainKey(id);
    verify(data).remove(any(DataKey.class));
  }

  @Test
  void logoutPersistsDebtBeforeDroppingTheRuntimeCopy() throws Exception {
    ChronosBorrowedTime borrowedTime = new ChronosBorrowedTime();
    PlayerRef player = mock(PlayerRef.class);
    DataHolder data = mock(DataHolder.class);
    UUID playerId = UUID.randomUUID();
    PlayerQuitEvent event = new PlayerQuitEvent(player);
    when(player.uuid()).thenReturn(playerId);
    when(player.data()).thenReturn(data);
    deferred(borrowedTime).put(
        playerId,
        new ConcurrentLinkedDeque<>(
            List.of(new ChronosBorrowedTime.DeferredDamage(1D, 2))
        )
    );

    borrowedTime.on(event);

    assertThat(deferred(borrowedTime)).doesNotContainKey(playerId);
    verify(data).set(any(), eq("1.0,2"));
    verify(data, never()).remove(any(DataKey.class));
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, Deque<ChronosBorrowedTime.DeferredDamage>> deferred(
      ChronosBorrowedTime borrowedTime
  ) throws Exception {
    Field field = ChronosBorrowedTime.class.getDeclaredField("deferred");
    field.setAccessible(true);
    return (Map<UUID, Deque<ChronosBorrowedTime.DeferredDamage>>) field.get(borrowedTime);
  }

  private static final class QuietBorrowedTime extends ChronosBorrowedTime {
    private final List<Double> paybacks = new ArrayList<>();
    private double damageCredit;

    @Override
    public int getActiveLevel(PlayerRef player) {
      return 5;
    }

    @Override
    protected void addStat(PlayerRef player, String stat, double amount) {
      damageCredit += amount;
    }

    @Override
    void emitDeferralFeedback(PlayerRef player) {
    }

    @Override
    protected void applyPlayerDamage(PlayerRef player, double amount) {
      assertThat(isApplyingPaybackDamage()).isTrue();
      paybacks.add(amount);
    }

    @Override
    void emitPaybackFeedback(PlayerRef player, double damage, boolean cleared) {
    }
  }

  private static PlayerRef damagePlayer() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.data()).thenReturn(mock(DataHolder.class));
    return player;
  }

  private static EntityDamageEvent damage(PlayerRef player, double damage, double absorption) {
    return new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), damage,
        base -> base - Math.min(base, absorption));
  }
}
