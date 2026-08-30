package art.arcane.adapt.api.notification;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptPlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifierTest extends AdaptTestBase {
  @Test
  void idleNotifierRegistersOnlyWhenWorkArrives() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    AdaptPlayer target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
    Notifier notifier = new Notifier(target);
    Notification notification = mock(Notification.class);

    verify(ticker, never()).register(notifier);

    notifier.queue(notification);

    verify(ticker).register(notifier);
  }

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
  void sameGroupNotificationsWithinOneBatchRemainOrdered() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    AdaptPlayer target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
    Notifier notifier = new Notifier(target);
    Notification firstSound = mock(Notification.class);
    Notification secondSound = mock(Notification.class);
    Notification popup = mock(Notification.class);
    when(firstSound.getGroup()).thenReturn("level");
    when(secondSound.getGroup()).thenReturn("level");
    when(popup.getGroup()).thenReturn("level");

    notifier.queue(firstSound, secondSound, popup);

    assertThat(notifier.pendingNotifications()).isEqualTo(3);
    notifier.onTick();
    notifier.onTick();
    notifier.onTick();

    InOrder order = inOrder(firstSound, secondSound, popup);
    order.verify(firstSound).play(target);
    order.verify(secondSound).play(target);
    order.verify(popup).play(target);
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
}
