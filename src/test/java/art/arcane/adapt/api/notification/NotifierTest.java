package art.arcane.adapt.api.notification;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.volmlib.util.math.M;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifierTest extends AdaptTestBase {
  @Test
  void queuedWorkWakesAnIdleNotifier() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    AdaptPlayer target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
    Notifier notifier = new Notifier(target);
    Notification notification = mock(Notification.class);

    assertThat(notifier.getInterval()).isEqualTo(Long.MAX_VALUE);

    notifier.queue(notification);

    assertThat(notifier.getInterval()).isZero();
    assertThat(notifier.isBursting()).isTrue();
  }

  @Test
  void groupedNotificationsKeepOnlyTheLatestPendingValue() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    AdaptPlayer target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
    Notifier notifier = new Notifier(target);
    Notification first = mock(Notification.class);
    Notification latest = mock(Notification.class);
    when(first.getGroup()).thenReturn("xp");
    when(latest.getGroup()).thenReturn("xp");

    notifier.queue(first);
    notifier.queue(latest);
    notifier.onTick();

    verify(first, never()).play(target);
    verify(latest).play(target);
    assertThat(notifier.pendingNotifications()).isZero();
  }

  @Test
  void pendingNotificationsHaveAHardPerPlayerCeiling() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    AdaptPlayer target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
    Notifier notifier = new Notifier(target);

    for (int index = 0; index < 100; index++) {
      Notification notification = mock(Notification.class);
      when(notification.getGroup()).thenReturn(Notification.DEFAULT_GROUP);
      notifier.queue(notification);
    }

    assertThat(notifier.pendingNotifications()).isEqualTo(64);
  }

  @Test
  void expiredXpBurstClearsItsHudSegment() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    AdaptPlayer target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
    AtomicLong now = new AtomicLong(1_000L);

    try (MockedStatic<M> clock = mockStatic(M.class);
         MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      clock.when(M::ms).thenAnswer(invocation -> now.get());
      Notifier notifier = new Notifier(target);
      notifier.notifyXP("discovery", 5.0D);

      now.set(5_000L);
      notifier.onTick();

      hud.verify(() -> AdaptHud.clearXp(player));
    }
  }
}
