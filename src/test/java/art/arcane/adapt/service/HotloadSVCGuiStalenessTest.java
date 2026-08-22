package art.arcane.adapt.service;

import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HotloadSVCGuiStalenessTest {
  @Test
  void entryIsStaleWhenPlayerIsMissing() {
    assertThat(HotloadSVC.isStaleGuiEntry(null, visibleWindow(UUID.randomUUID()))).isTrue();
  }

  @Test
  void entryIsStaleWhenWindowIsMissing() {
    assertThat(HotloadSVC.isStaleGuiEntry(onlinePlayer(UUID.randomUUID()), null)).isTrue();
  }

  @Test
  void entryIsStaleWhenPlayerIsOffline() {
    UUID id = UUID.randomUUID();
    Player player = onlinePlayer(id);
    when(player.isOnline()).thenReturn(false);

    assertThat(HotloadSVC.isStaleGuiEntry(player, visibleWindow(id))).isTrue();
  }

  @Test
  void entryIsStaleWhenTheWindowIsNoLongerVisible() {
    UUID id = UUID.randomUUID();
    UIWindow window = visibleWindow(id);
    when(window.isVisible()).thenReturn(false);

    assertThat(HotloadSVC.isStaleGuiEntry(onlinePlayer(id), window)).isTrue();
  }

  @Test
  void entryIsStaleWhenTheWindowBelongsToAnotherViewer() {
    UIWindow window = visibleWindow(UUID.randomUUID());

    assertThat(HotloadSVC.isStaleGuiEntry(onlinePlayer(UUID.randomUUID()), window)).isTrue();
  }

  @Test
  void entryIsStaleWhenTheWindowHasNoViewer() {
    UUID id = UUID.randomUUID();
    UIWindow window = mock(UIWindow.class);
    when(window.isVisible()).thenReturn(true);
    when(window.getViewer()).thenReturn(null);

    assertThat(HotloadSVC.isStaleGuiEntry(onlinePlayer(id), window)).isTrue();
  }

  @Test
  void entryIsFreshWhenTheOnlineViewerStillHasTheWindowOpen() {
    UUID id = UUID.randomUUID();

    assertThat(HotloadSVC.isStaleGuiEntry(onlinePlayer(id), visibleWindow(id))).isFalse();
  }

  private Player onlinePlayer(UUID id) {
    Player player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    return player;
  }

  private UIWindow visibleWindow(UUID viewerId) {
    Player viewer = onlinePlayer(viewerId);
    UIWindow window = mock(UIWindow.class);
    lenient().when(window.isVisible()).thenReturn(true);
    lenient().when(window.getViewer()).thenReturn(viewer);
    return window;
  }
}
