package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.notification.Notification;
import art.arcane.adapt.api.notification.Notifier;
import art.arcane.adapt.api.notification.TitleNotification;
import art.arcane.adapt.api.xp.XP;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlayerDataProgressionNotificationTest extends AdaptTestBase {
  private Field configField;
  private AdaptConfig previousConfig;
  private AdaptConfig config;

  @BeforeEach
  void installProgressionConfig() throws Exception {
    configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previousConfig = (AdaptConfig) configField.get(null);
    config = new AdaptConfig();
    configField.set(null, config);
    AdaptServer server = mock(AdaptServer.class);
    lenient().when(server.getData()).thenReturn(new AdaptServerData());
    lenient().when(plugin.getAdaptServer()).thenReturn(server);
  }

  @AfterEach
  void restoreProgressionConfig() throws Exception {
    configField.set(null, previousConfig);
  }

  @Test
  void disabledMasterPopupAndSoundsStillAdvanceMasterLevelState() throws Exception {
    configure(false, false);
    Fixture fixture = fixture();

    fixture.data().update(fixture.player());

    verifyNoInteractions(fixture.notifications(), fixture.actionBarNotifications());
    assertThat(fixture.data().getLastMasterXp()).isEqualTo(fixture.data().getMasterXp());
  }

  @Test
  void masterPopupCanRemainVisibleWhileProgressionSoundsAreMuted() throws Exception {
    configure(true, false);
    Fixture fixture = fixture();
    ArgumentCaptor<Notification[]> notifications = ArgumentCaptor.forClass(Notification[].class);

    fixture.data().update(fixture.player());

    verify(fixture.notifications()).queue(notifications.capture());
    assertThat(notifications.getValue())
        .hasSize(1)
        .allMatch(TitleNotification.class::isInstance);
    verify(fixture.actionBarNotifications()).queue(any(Notification[].class));
  }

  private void configure(boolean popupEnabled, boolean soundsEnabled) throws Exception {
    setBoolean("actionbarNotifyMasterLevel", popupEnabled);
    setBoolean("progressionSoundsEnabled", soundsEnabled);
  }

  private void setBoolean(String name, boolean value) throws Exception {
    Field field = AdaptConfig.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setBoolean(config, value);
  }

  private Fixture fixture() {
    PlayerData data = new PlayerData();
    data.setLastMasterXp(0D);
    data.setMasterXp(XP.getXpForLevel(2D));
    Player bukkitPlayer = mock(Player.class);
    Notifier notifications = mock(Notifier.class);
    Notifier actionBarNotifications = mock(Notifier.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    when(player.getPlayer()).thenReturn(bukkitPlayer);
    when(player.getNot()).thenReturn(notifications);
    when(player.getActionBarNotifier()).thenReturn(actionBarNotifications);
    return new Fixture(data, player, notifications, actionBarNotifications);
  }

  private record Fixture(
      PlayerData data,
      AdaptPlayer player,
      Notifier notifications,
      Notifier actionBarNotifications
  ) {
  }
}
