package art.arcane.adapt.api.notification;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptPlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NotificationDisplayDurationTest extends AdaptTestBase {
  @Test
  void actionBarNotificationPassesItsDisplayDurationToTheHud() {
    Player player = mock(Player.class);
    AdaptPlayer target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
    ActionBarNotification notification = ActionBarNotification.builder()
        .group("xp")
        .title("+12XP")
        .displayDurationMillis(4_200L)
        .build();

    try (MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      notification.play(target);

      hud.verify(() -> AdaptHud.xpTicker(player, "+12XP", 4_200L));
    }
  }

  @Test
  void titleNotificationPassesItsDisplayDurationToTheHud() {
    Player player = mock(Player.class);
    AdaptPlayer target = mock(AdaptPlayer.class);
    when(target.getPlayer()).thenReturn(player);
    TitleNotification notification = TitleNotification.builder()
        .title("")
        .subtitle("Level 12")
        .displayDurationMillis(900L)
        .build();

    try (MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      notification.play(target);

      hud.verify(() -> AdaptHud.title(player, " ", "Level 12", 900L));
    }
  }
}
