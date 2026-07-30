package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChronosBorrowedTimeDamageTest extends AdaptTestBase {
  @BeforeEach
  void configurePluginName() {
    lenient().when(plugin.getName()).thenReturn("Adapt");
    lenient().when(plugin.namespace()).thenReturn("adapt");
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
  void logoutPersistsDebtBeforeDroppingTheRuntimeCopy() throws Exception {
    ChronosBorrowedTime borrowedTime = new ChronosBorrowedTime();
    Player player = mock(Player.class);
    PlayerQuitEvent event = mock(PlayerQuitEvent.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    UUID playerId = UUID.randomUUID();
    when(event.getPlayer()).thenReturn(player);
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.getPersistentDataContainer()).thenReturn(data);
    deferred(borrowedTime).put(
        playerId,
        new ConcurrentLinkedDeque<>(
            List.of(new ChronosBorrowedTime.DeferredDamage(1D, 2))
        )
    );

    borrowedTime.on(event);

    assertThat(deferred(borrowedTime)).doesNotContainKey(playerId);
    verify(data).set(any(), same(PersistentDataType.STRING), eq("1.0,2"));
    verify(data, never()).remove(any());
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, Deque<ChronosBorrowedTime.DeferredDamage>> deferred(
      ChronosBorrowedTime borrowedTime
  ) throws Exception {
    Field field = ChronosBorrowedTime.class.getDeclaredField("deferred");
    field.setAccessible(true);
    return (Map<UUID, Deque<ChronosBorrowedTime.DeferredDamage>>) field.get(borrowedTime);
  }
}
